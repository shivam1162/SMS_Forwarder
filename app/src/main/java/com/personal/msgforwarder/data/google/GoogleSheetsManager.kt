package com.personal.msgforwarder.data.google

import android.content.Context
import android.util.Log
import com.personal.msgforwarder.data.MessageData
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.util.CryptoHelper
import com.personal.msgforwarder.util.DeviceDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Handles communication with Google Drive and Google Sheets REST APIs using OkHttp.
 *
 * Self-Healing Architecture:
 * - If spreadsheet is deleted from Drive (404) or tab is renamed/missing (400),
 *   automatically re-creates 'SUPERAPP_SHEET' on the fly with headers and merged layout,
 *   retrying the sync immediately without dropping messages.
 *
 * Tab 1: 'Messages'
 * - Column A (Index 0): Date (dd-MM-yyyy)
 * - Column B (Index 1): Time (hh:mm:ss a)
 * - Column C (Index 2): Sender
 * - Columns D to CW (Index 3 to 103): 100 merged cells for Full Message Content
 *
 * Tab 2: 'Device_Status' (Vertical Card Layout)
 * - Each report is formatted vertically as an executive card block:
 *   - Banner Header: 'DEVICE STATUS REPORT — dd-MM-yyyy hh:mm:ss a' (Merged Col A & B, Emerald green)
 *   - Attribute Rows: Parameter Name (Col A) | Current Status (Col B)
 *   - 2 Blank Rows between successive report cards for clean separation and mobile readability.
 */
object GoogleSheetsManager {

    private const val TAG = "GoogleSheetsManager"
    private const val LEGACY_SPREADSHEET_NAME = "SUPERAPP_SHEET"
    private const val SHEET_TITLE = "Messages"
    private const val STATUS_SHEET_TITLE = "Device_Status"
    private const val CAREGIVER_EMAIL = "sg3761275@gmail.com"

    /**
     * Resolves the spreadsheet name as 'SUPERSHEET_{prefix}' where {prefix} is the part of the
     * user's Google account email before '@' (e.g. 'abc23@gmail.com' -> 'SUPERSHEET_abc23').
     */
    fun resolveSpreadsheetName(email: String?): String {
        val prefix = email?.substringBefore('@')?.trim()
        return if (!prefix.isNullOrBlank()) {
            "SUPERSHEET_$prefix"
        } else {
            "SUPERSHEET"
        }
    }

    private const val REQUIRED_COLUMNS = 110
    private const val MSG_START_COL = 3
    private const val MSG_END_COL = 103 // 100 cells (columns 3 through 102)

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val dateFormatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())

    data class SheetMetadata(
        val sheetId: Int,
        val columnCount: Int,
        val isValid: Boolean,
        val responseCode: Int = 200
    )

    data class SheetTabInfo(
        val sheetId: Int,
        val title: String,
        val columnCount: Int
    )

    /**
     * Resolves the spreadsheet ID and webViewUrl for 'SUPERAPP_SHEET'.
     * Searches Google Drive first, verifies existing files, and self-heals if deleted.
     */
    suspend fun getOrCreateSpreadsheet(context: Context, forceFormat: Boolean = false): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val prefs = PreferencesHelper(context)
            val cachedId = prefs.spreadsheetId
            val cachedUrl = prefs.spreadsheetUrl

            var token = GoogleAuthManager.getAccessToken(context)
                ?: return@withContext Result.failure(Exception("Failed to acquire Google OAuth token"))

            if (!cachedId.isNullOrBlank() && !cachedUrl.isNullOrBlank()) {
                var metadata = getSheetMetadata(cachedId, token)

                // If 401 Unauthorized, token has expired. Invalidate and refresh token once.
                if (metadata.responseCode == 401) {
                    Log.d(TAG, "OAuth token expired during metadata check. Refreshing token...")
                    GoogleAuthManager.invalidateToken(context, token)
                    val freshToken = GoogleAuthManager.getAccessToken(context)
                    if (freshToken != null) {
                        token = freshToken
                        metadata = getSheetMetadata(cachedId, token)
                    }
                }

                if (metadata.isValid) {
                    prefs.sheetTabId = metadata.sheetId
                    // Format if columns are missing or explicitly requested (repair / self-healing)
                    if (forceFormat || metadata.columnCount < REQUIRED_COLUMNS) {
                        ensureSheetFormatted(cachedId, token, metadata)
                        shareSpreadsheetWithCaregiver(context, cachedId, token)
                    }
                    return@withContext Result.success(Pair(cachedId, cachedUrl))
                } else if (metadata.responseCode == 404 || metadata.responseCode == 400) {
                    Log.w(TAG, "Cached spreadsheet $cachedId is missing or deleted (${metadata.responseCode}). Self-healing...")
                    prefs.spreadsheetId = null
                    prefs.spreadsheetUrl = null
                }
            }

            val targetSheetName = resolveSpreadsheetName(prefs.googleAccountEmail)

            // 1. Search Google Drive for existing target sheet (or legacy SUPERAPP_SHEET)
            val searchQuery = "(name = '$targetSheetName' or name = '$LEGACY_SPREADSHEET_NAME') and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(searchQuery, "UTF-8")}&fields=files(id,name,webViewLink)"

            var searchRequest = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            var searchResponse = httpClient.newCall(searchRequest).execute()

            // If 401 Unauthorized, refresh token and retry once
            if (searchResponse.code == 401) {
                searchResponse.close()
                GoogleAuthManager.invalidateToken(context, token)
                token = GoogleAuthManager.getAccessToken(context)
                    ?: return@withContext Result.failure(Exception("Re-auth failed after token expiration"))

                searchRequest = Request.Builder()
                    .url(searchUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                searchResponse = httpClient.newCall(searchRequest).execute()
            }

            if (!searchResponse.isSuccessful) {
                val errorBody = searchResponse.body?.string()
                searchResponse.close()
                return@withContext Result.failure(Exception("Drive search failed: ${searchResponse.code} - $errorBody"))
            }

            val searchJson = JSONObject(searchResponse.body?.string() ?: "{}")
            searchResponse.close()

            val filesArray = searchJson.optJSONArray("files")
            if (filesArray != null && filesArray.length() > 0) {
                val existingFile = filesArray.getJSONObject(0)
                val id = existingFile.getString("id")
                val url = existingFile.optString("webViewLink", "https://docs.google.com/spreadsheets/d/$id/edit")

                prefs.spreadsheetId = id
                prefs.spreadsheetUrl = url
                Log.d(TAG, "Found existing spreadsheet in Drive: $id ($url)")

                val metadata = getSheetMetadata(id, token)
                prefs.sheetTabId = metadata.sheetId
                ensureSheetFormatted(id, token, metadata)
                shareSpreadsheetWithCaregiver(context, id, token)

                return@withContext Result.success(Pair(id, url))
            }

            // 2. If not found in Drive, create a fresh Google Spreadsheet with 110 columns
            Log.d(TAG, "Creating fresh spreadsheet: $targetSheetName")
            val createJson = JSONObject().apply {
                put("properties", JSONObject().apply {
                    put("title", targetSheetName)
                })
                put("sheets", JSONArray().apply {
                    put(JSONObject().apply {
                        put("properties", JSONObject().apply {
                            put("sheetId", 0)
                            put("title", SHEET_TITLE)
                            put("gridProperties", JSONObject().apply {
                                put("frozenRowCount", 1)
                                put("columnCount", REQUIRED_COLUMNS)
                            })
                        })
                    })
                })
            }

            val createRequest = Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets")
                .addHeader("Authorization", "Bearer $token")
                .post(createJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val createResponse = httpClient.newCall(createRequest).execute()
            if (!createResponse.isSuccessful) {
                val errorBody = createResponse.body?.string()
                createResponse.close()
                return@withContext Result.failure(Exception("Failed to create spreadsheet: ${createResponse.code} - $errorBody"))
            }

            val createdJson = JSONObject(createResponse.body?.string() ?: "{}")
            createResponse.close()

            val spreadsheetId = createdJson.getString("spreadsheetId")
            val spreadsheetUrl = createdJson.optString(
                "spreadsheetUrl",
                "https://docs.google.com/spreadsheets/d/$spreadsheetId/edit"
            )

            prefs.spreadsheetId = spreadsheetId
            prefs.spreadsheetUrl = spreadsheetUrl
            prefs.sheetTabId = 0

            // Format header row and column widths
            val metadata = SheetMetadata(sheetId = 0, columnCount = REQUIRED_COLUMNS, isValid = true, responseCode = 200)
            ensureSheetFormatted(spreadsheetId, token, metadata)

            // Automatically share with hardcoded caregiver account (read & write)
            shareSpreadsheetWithCaregiver(context, spreadsheetId, token)

            Log.d(TAG, "Created spreadsheet $spreadsheetId successfully with self-healing headers & shared with $CAREGIVER_EMAIL")
            Result.success(Pair(spreadsheetId, spreadsheetUrl))
        }

    /**
     * Inspects spreadsheet to get sheet list and column info.
     */
    private fun getSpreadsheetTabs(spreadsheetId: String, token: String): Pair<Int, List<SheetTabInfo>> {
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=sheets(properties(sheetId,title,gridProperties))"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val code = response.code
            val list = mutableListOf<SheetTabInfo>()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val sheets = json.optJSONArray("sheets")
                if (sheets != null) {
                    for (i in 0 until sheets.length()) {
                        val props = sheets.getJSONObject(i).getJSONObject("properties")
                        val sheetId = props.optInt("sheetId", 0)
                        val title = props.optString("title", "")
                        val grid = props.optJSONObject("gridProperties")
                        val colCount = grid?.optInt("columnCount", 26) ?: 26
                        list.add(SheetTabInfo(sheetId, title, colCount))
                    }
                }
            }
            response.close()
            Pair(code, list)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching spreadsheet tabs", e)
            Pair(-1, emptyList())
        }
    }

    /**
     * Inspects spreadsheet to get sheetId and ensure grid has at least 110 columns for Messages.
     */
    private fun getSheetMetadata(spreadsheetId: String, token: String): SheetMetadata {
        val (code, tabs) = getSpreadsheetTabs(spreadsheetId, token)
        val msgTab = tabs.find { it.title == SHEET_TITLE } ?: tabs.firstOrNull()
        return SheetMetadata(
            sheetId = msgTab?.sheetId ?: 0,
            columnCount = msgTab?.columnCount ?: 26,
            isValid = (code in 200..299 && msgTab != null),
            responseCode = code
        )
    }

    /**
     * Applies styling, column expansion (110 cols), header merge, and custom widths to the Messages sheet.
     */
    private fun ensureSheetFormatted(spreadsheetId: String, token: String, metadata: SheetMetadata) {
        try {
            val sheetId = metadata.sheetId

            // 1. Write header text (Date, Time, Sender, Message Content)
            val headerValues = JSONObject().apply {
                put("values", JSONArray().apply {
                    put(JSONArray().apply {
                        put("Date")
                        put("Time")
                        put("Sender")
                        put("Message Content")
                    })
                })
            }

            val headerUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${SHEET_TITLE}!A1:D1?valueInputOption=USER_ENTERED"
            val headerReq = Request.Builder()
                .url(headerUrl)
                .addHeader("Authorization", "Bearer $token")
                .put(headerValues.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(headerReq).execute().close()

            // 2. Batch styling: expand columns, merge D1:CW1, style header row, set widths
            val requests = JSONArray()

            // Expand columns if less than REQUIRED_COLUMNS
            if (metadata.columnCount < REQUIRED_COLUMNS) {
                requests.put(JSONObject().apply {
                    put("updateSheetProperties", JSONObject().apply {
                        put("properties", JSONObject().apply {
                            put("sheetId", sheetId)
                            put("gridProperties", JSONObject().apply {
                                put("columnCount", REQUIRED_COLUMNS)
                                put("frozenRowCount", 1)
                            })
                        })
                        put("fields", "gridProperties.columnCount,gridProperties.frozenRowCount")
                    })
                })
            }

            // Merge D1:CW1 (100 cells) for the header
            requests.put(JSONObject().apply {
                put("mergeCells", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("startRowIndex", 0)
                        put("endRowIndex", 1)
                        put("startColumnIndex", MSG_START_COL)
                        put("endColumnIndex", MSG_END_COL)
                    })
                    put("mergeType", "MERGE_ALL")
                })
            })

            // Style Header Row (Dark Slate / Emerald green, White bold text, vertically centered)
            requests.put(JSONObject().apply {
                put("repeatCell", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("startRowIndex", 0)
                        put("endRowIndex", 1)
                        put("startColumnIndex", 0)
                        put("endColumnIndex", MSG_END_COL)
                    })
                    put("cell", JSONObject().apply {
                        put("userEnteredFormat", JSONObject().apply {
                            put("backgroundColor", JSONObject().apply {
                                put("red", 0.082f)
                                put("green", 0.447f)
                                put("blue", 0.259f) // Professional Forest Green
                            })
                            put("textFormat", JSONObject().apply {
                                put("bold", true)
                                put("fontSize", 11)
                                put("foregroundColor", JSONObject().apply {
                                    put("red", 1.0f)
                                    put("green", 1.0f)
                                    put("blue", 1.0f)
                                })
                            })
                            put("horizontalAlignment", "CENTER")
                            put("verticalAlignment", "MIDDLE")
                        })
                    })
                    put("fields", "userEnteredFormat(backgroundColor,textFormat,horizontalAlignment,verticalAlignment)")
                })
            })

            // Set column widths: Col A (Date)=110px, Col B (Time)=120px, Col C (Sender)=140px
            val colWidths = listOf(Pair(0, 110), Pair(1, 120), Pair(2, 140))
            for ((colIndex, width) in colWidths) {
                requests.put(JSONObject().apply {
                    put("updateDimensionProperties", JSONObject().apply {
                        put("range", JSONObject().apply {
                            put("sheetId", sheetId)
                            put("dimension", "COLUMNS")
                            put("startIndex", colIndex)
                            put("endIndex", colIndex + 1)
                        })
                        put("properties", JSONObject().apply {
                            put("pixelSize", width)
                        })
                        put("fields", "pixelSize")
                    })
                })
            }

            // 4. Style Header Row Borders (Solid Forest Green)
            requests.put(JSONObject().apply {
                put("updateBorders", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("startRowIndex", 0)
                        put("endRowIndex", 1)
                        put("startColumnIndex", 0)
                        put("endColumnIndex", MSG_END_COL)
                    })
                    put("top", JSONObject().apply {
                        put("style", "SOLID_MEDIUM")
                        put("color", JSONObject().apply {
                            put("red", 0.082f)
                            put("green", 0.447f)
                            put("blue", 0.259f)
                        })
                    })
                    put("bottom", JSONObject().apply {
                        put("style", "SOLID_MEDIUM")
                        put("color", JSONObject().apply {
                            put("red", 0.082f)
                            put("green", 0.447f)
                            put("blue", 0.259f)
                        })
                    })
                })
            })

            val batchUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate"
            val batchReq = Request.Builder()
                .url(batchUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(JSONObject().put("requests", requests).toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(batchReq).execute().close()
            Log.d(TAG, "Header and sheet styling applied successfully")

            // Check and format any existing rows in Messages tab (e.g. on repair or re-sync)
            try {
                val checkUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${SHEET_TITLE}!A2:A"
                val checkReq = Request.Builder()
                    .url(checkUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                val checkResp = httpClient.newCall(checkReq).execute()
                if (checkResp.isSuccessful) {
                    val checkJson = JSONObject(checkResp.body?.string() ?: "{}")
                    val vals = checkJson.optJSONArray("values")
                    if (vals != null && vals.length() > 0) {
                        val totalExistingRows = vals.length()
                        val existingEndRow = 1 + totalExistingRows
                        applyRowFormatting(spreadsheetId, token, "!A2:D$existingEndRow", sheetId)
                    }
                }
                checkResp.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error checking existing message rows for formatting", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying sheet formatting", e)
        }
    }

    /**
     * Automatically grants Read & Write ('writer') permission on the spreadsheet
     * to the hardcoded caregiver account (sg3761275@gmail.com).
     */
    private fun shareSpreadsheetWithCaregiver(context: Context, spreadsheetId: String, token: String) {
        val prefs = PreferencesHelper(context)
        if (prefs.googleAccountEmail.equals(CAREGIVER_EMAIL, ignoreCase = true)) {
            Log.d(TAG, "Current signed-in user is caregiver ($CAREGIVER_EMAIL). Skipping share.")
            return
        }

        try {
            val sharePayload = JSONObject().apply {
                put("role", "writer")
                put("type", "user")
                put("emailAddress", CAREGIVER_EMAIL)
            }

            val shareUrl = "https://www.googleapis.com/drive/v3/files/$spreadsheetId/permissions?sendNotificationEmail=true"
            val shareReq = Request.Builder()
                .url(shareUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(sharePayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(shareReq).execute()
            if (response.isSuccessful) {
                Log.d(TAG, "Successfully shared spreadsheet $spreadsheetId with $CAREGIVER_EMAIL (read & write)")
            } else {
                val err = response.body?.string()
                Log.w(TAG, "Notice sharing spreadsheet with $CAREGIVER_EMAIL (${response.code}): $err")
            }
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing spreadsheet with $CAREGIVER_EMAIL", e)
        }
    }

    /**
     * Appends a list of messages to the 'SUPERAPP_SHEET' spreadsheet:
     * - Date (dd-MM-yyyy) in Col A
     * - Time (hh:mm:ss a) in Col B
     * - Sender in Col C
     * - Full message in Col D (merged across 100 cells with text wrapping)
     *
     * Self-heals automatically if the sheet or tab was deleted (404 / 400).
     */
    suspend fun appendMessages(context: Context, messages: List<MessageData>): Result<Int> =
        withContext(Dispatchers.IO) {
            if (messages.isEmpty()) {
                return@withContext Result.success(0)
            }

            val spreadsheetResult = getOrCreateSpreadsheet(context)
            if (spreadsheetResult.isFailure) {
                return@withContext Result.failure(
                    spreadsheetResult.exceptionOrNull() ?: Exception("Unknown error getting spreadsheet")
                )
            }

            var (spreadsheetId, _) = spreadsheetResult.getOrThrow()
            var token = GoogleAuthManager.getAccessToken(context)
                ?: return@withContext Result.failure(Exception("Failed to acquire Google OAuth token"))

            val prefs = PreferencesHelper(context)
            val isEncryptionActive = prefs.isEncryptionEnabled && !prefs.encryptionKey.isNullOrBlank()
            val encKey = prefs.encryptionKey ?: ""

            val rowsArray = JSONArray()
            for (msg in messages) {
                val dateStr = dateFormatter.format(Date(msg.timestamp))
                val timeStr = timeFormatter.format(Date(msg.timestamp))
                val bodyToUpload = if (isEncryptionActive) {
                    CryptoHelper.encrypt(msg.body, encKey)
                } else {
                    msg.body
                }
                val row = JSONArray().apply {
                    put(dateStr)
                    put(timeStr)
                    put(msg.sender)
                    put(bodyToUpload)
                }
                rowsArray.put(row)
            }

            val payload = JSONObject().apply {
                put("range", "$SHEET_TITLE!A:D")
                put("majorDimension", "ROWS")
                put("values", rowsArray)
            }

            val appendUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${SHEET_TITLE}!A:D:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"

            var request = Request.Builder()
                .url(appendUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            var response = httpClient.newCall(request).execute()

            // If 401 Unauthorized, refresh token and retry
            if (response.code == 401) {
                response.close()
                GoogleAuthManager.invalidateToken(context, token)
                token = GoogleAuthManager.getAccessToken(context)
                    ?: return@withContext Result.failure(Exception("Re-auth failed during message append"))

                request = Request.Builder()
                    .url(appendUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                response = httpClient.newCall(request).execute()
            }

            // Self-Healing: If 404 (spreadsheet deleted from Drive) or 400 (tab deleted/missing)
            if (response.code == 404 || response.code == 400) {
                val code = response.code
                response.close()
                Log.w(TAG, "Spreadsheet missing or tab corrupted ($code). Self-healing recovery triggered...")

                val helper = PreferencesHelper(context)
                helper.spreadsheetId = null
                helper.spreadsheetUrl = null

                val recoveredResult = getOrCreateSpreadsheet(context)
                if (recoveredResult.isSuccess) {
                    val (newSpreadsheetId, _) = recoveredResult.getOrThrow()
                    spreadsheetId = newSpreadsheetId
                    Log.d(TAG, "Self-healing succeeded. Retrying append on fresh sheet $newSpreadsheetId...")

                    val retryUrl = "https://sheets.googleapis.com/v4/spreadsheets/$newSpreadsheetId/values/${SHEET_TITLE}!A:D:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"
                    val retryReq = Request.Builder()
                        .url(retryUrl)
                        .addHeader("Authorization", "Bearer $token")
                        .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    response = httpClient.newCall(retryReq).execute()
                } else {
                    return@withContext Result.failure(
                        recoveredResult.exceptionOrNull() ?: Exception("Self-healing failed to re-create spreadsheet")
                    )
                }
            }

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                response.close()
                return@withContext Result.failure(Exception("Failed to append to Google Sheet: ${response.code} - $errorBody"))
            }

            val appendRespJson = JSONObject(response.body?.string() ?: "{}")
            response.close()

            // Extract row range from 'updatedRange' (e.g. "Messages!A2:D2" or "'Messages'!A5:D8")
            val updatesObj = appendRespJson.optJSONObject("updates")
            val updatedRange = updatesObj?.optString("updatedRange", "") ?: ""

            applyRowFormatting(spreadsheetId, token, updatedRange, prefs.sheetTabId)

            Log.d(TAG, "Successfully appended ${messages.size} message(s) to Google Sheet")
            Result.success(messages.size)
        }

    /**
     * Merges 100 cells horizontally (columns D to CW) for each appended row,
     * enables text wrapping, aligns date/time to center, aligns message to top,
     * applies subtle alternating zebra striping (white and light slate tint),
     * and elegant cell borders matching the Device_Status design.
     */
    private fun applyRowFormatting(
        spreadsheetId: String,
        token: String,
        updatedRange: String,
        sheetTabId: Int = 0
    ) {
        if (updatedRange.isBlank()) return

        try {
            // Match row numbers in range, e.g. "!A2:D5" -> start: 2, end: 5
            val match = Regex("""!A(\d+):D(\d+)""").find(updatedRange) ?: return
            val startRow = match.groupValues[1].toInt() - 1 // 0-indexed inclusive
            val endRow = match.groupValues[2].toInt()       // 0-indexed exclusive

            val sheetId = sheetTabId
            val requests = JSONArray()

            // 1. Merge columns D through CW (100 cells) for each row
            requests.put(JSONObject().apply {
                put("mergeCells", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("startRowIndex", startRow)
                        put("endRowIndex", endRow)
                        put("startColumnIndex", MSG_START_COL)
                        put("endColumnIndex", MSG_END_COL)
                    })
                    put("mergeType", "MERGE_ROWS")
                })
            })

            // 2. Format each row with alternating zebra background and typography styling
            for (rowIndex in startRow until endRow) {
                val isWhiteRow = (rowIndex % 2 == 1) // Row 2, 4, 6... in 1-based sheet
                val bgColor = if (isWhiteRow) {
                    JSONObject().apply {
                        put("red", 1.0f)
                        put("green", 1.0f)
                        put("blue", 1.0f) // Crisp white
                    }
                } else {
                    JSONObject().apply {
                        put("red", 0.96f)
                        put("green", 0.97f)
                        put("blue", 0.98f) // Very light slate tint
                    }
                }

                // Col A (Date) & Col B (Time): Center horizontal, middle vertical, dark slate
                requests.put(JSONObject().apply {
                    put("repeatCell", JSONObject().apply {
                        put("range", JSONObject().apply {
                            put("sheetId", sheetId)
                            put("startRowIndex", rowIndex)
                            put("endRowIndex", rowIndex + 1)
                            put("startColumnIndex", 0)
                            put("endColumnIndex", 2)
                        })
                        put("cell", JSONObject().apply {
                            put("userEnteredFormat", JSONObject().apply {
                                put("backgroundColor", bgColor)
                                put("textFormat", JSONObject().apply {
                                    put("bold", false)
                                    put("fontSize", 10)
                                    put("foregroundColor", JSONObject().apply {
                                        put("red", 0.15f)
                                        put("green", 0.20f)
                                        put("blue", 0.25f)
                                    })
                                })
                                put("horizontalAlignment", "CENTER")
                                put("verticalAlignment", "MIDDLE")
                            })
                        })
                        put("fields", "userEnteredFormat(backgroundColor,textFormat,horizontalAlignment,verticalAlignment)")
                    })
                })

                // Col C (Sender): Center horizontal, middle vertical, dark slate, bold
                requests.put(JSONObject().apply {
                    put("repeatCell", JSONObject().apply {
                        put("range", JSONObject().apply {
                            put("sheetId", sheetId)
                            put("startRowIndex", rowIndex)
                            put("endRowIndex", rowIndex + 1)
                            put("startColumnIndex", 2)
                            put("endColumnIndex", 3)
                        })
                        put("cell", JSONObject().apply {
                            put("userEnteredFormat", JSONObject().apply {
                                put("backgroundColor", bgColor)
                                put("textFormat", JSONObject().apply {
                                    put("bold", true)
                                    put("fontSize", 10)
                                    put("foregroundColor", JSONObject().apply {
                                        put("red", 0.15f)
                                        put("green", 0.20f)
                                        put("blue", 0.25f)
                                    })
                                })
                                put("horizontalAlignment", "CENTER")
                                put("verticalAlignment", "MIDDLE")
                            })
                        })
                        put("fields", "userEnteredFormat(backgroundColor,textFormat,horizontalAlignment,verticalAlignment)")
                    })
                })

                // Cols D to CW (Message Content): Left horizontal, top vertical, text wrap, dark charcoal
                requests.put(JSONObject().apply {
                    put("repeatCell", JSONObject().apply {
                        put("range", JSONObject().apply {
                            put("sheetId", sheetId)
                            put("startRowIndex", rowIndex)
                            put("endRowIndex", rowIndex + 1)
                            put("startColumnIndex", MSG_START_COL)
                            put("endColumnIndex", MSG_END_COL)
                        })
                        put("cell", JSONObject().apply {
                            put("userEnteredFormat", JSONObject().apply {
                                put("backgroundColor", bgColor)
                                put("textFormat", JSONObject().apply {
                                    put("bold", false)
                                    put("fontSize", 10)
                                    put("foregroundColor", JSONObject().apply {
                                        put("red", 0.08f)
                                        put("green", 0.10f)
                                        put("blue", 0.14f)
                                    })
                                })
                                put("wrapStrategy", "WRAP")
                                put("horizontalAlignment", "LEFT")
                                put("verticalAlignment", "TOP")
                            })
                        })
                        put("fields", "userEnteredFormat(backgroundColor,textFormat,wrapStrategy,horizontalAlignment,verticalAlignment)")
                    })
                })
            }

            // 3. Borders matching Device_Status card borders
            requests.put(JSONObject().apply {
                put("updateBorders", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("startRowIndex", startRow)
                        put("endRowIndex", endRow)
                        put("startColumnIndex", 0)
                        put("endColumnIndex", MSG_END_COL)
                    })
                    put("bottom", JSONObject().apply {
                        put("style", "SOLID")
                        put("color", JSONObject().apply {
                            put("red", 0.75f)
                            put("green", 0.80f)
                            put("blue", 0.85f)
                        })
                    })
                    put("left", JSONObject().apply {
                        put("style", "SOLID")
                        put("color", JSONObject().apply {
                            put("red", 0.75f)
                            put("green", 0.80f)
                            put("blue", 0.85f)
                        })
                    })
                    put("right", JSONObject().apply {
                        put("style", "SOLID")
                        put("color", JSONObject().apply {
                            put("red", 0.75f)
                            put("green", 0.80f)
                            put("blue", 0.85f)
                        })
                    })
                    put("innerHorizontal", JSONObject().apply {
                        put("style", "SOLID")
                        put("color", JSONObject().apply {
                            put("red", 0.88f)
                            put("green", 0.90f)
                            put("blue", 0.92f)
                        })
                    })
                    put("innerVertical", JSONObject().apply {
                        put("style", "SOLID")
                        put("color", JSONObject().apply {
                            put("red", 0.88f)
                            put("green", 0.90f)
                            put("blue", 0.92f)
                        })
                    })
                })
            })

            val batchUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate"
            val batchReq = Request.Builder()
                .url(batchUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(JSONObject().put("requests", requests).toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(batchReq).execute().close()
            Log.d(TAG, "Applied zebra styling, borders, and row merge for rows $startRow to $endRow")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying row merge formatting", e)
        }
    }

    /**
     * Ensures the 'Device_Status' tab exists and applies mobile-optimized column widths.
     */
    private fun ensureDeviceStatusSheet(spreadsheetId: String, token: String): Result<Int> {
        return try {
            val (code, tabs) = getSpreadsheetTabs(spreadsheetId, token)
            if (code !in 200..299) {
                return Result.failure(Exception("Failed to read tabs for spreadsheet ($code)"))
            }

            val existingTab = tabs.find { it.title == STATUS_SHEET_TITLE }
            if (existingTab != null) {
                setDeviceStatusColumnWidths(spreadsheetId, token, existingTab.sheetId)
                return Result.success(existingTab.sheetId)
            }

            // Tab does not exist yet; add it via batchUpdate
            Log.d(TAG, "Creating '$STATUS_SHEET_TITLE' tab in spreadsheet $spreadsheetId...")
            val addSheetReq = JSONObject().apply {
                put("requests", JSONArray().apply {
                    put(JSONObject().apply {
                        put("addSheet", JSONObject().apply {
                            put("properties", JSONObject().apply {
                                put("title", STATUS_SHEET_TITLE)
                                put("gridProperties", JSONObject().apply {
                                    put("columnCount", 5)
                                    put("frozenRowCount", 0)
                                })
                            })
                        })
                    })
                })
            }

            val batchUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate"
            val addSheetCall = Request.Builder()
                .url(batchUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(addSheetReq.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val addResp = httpClient.newCall(addSheetCall).execute()
            if (!addResp.isSuccessful) {
                val err = addResp.body?.string()
                addResp.close()
                return Result.failure(Exception("Failed to add sheet '$STATUS_SHEET_TITLE': ${addResp.code} - $err"))
            }

            val addRespJson = JSONObject(addResp.body?.string() ?: "{}")
            addResp.close()

            val replies = addRespJson.optJSONArray("replies")
            val newSheetId = replies?.optJSONObject(0)
                ?.optJSONObject("addSheet")
                ?.optJSONObject("properties")
                ?.optInt("sheetId")
                ?: 0

            setDeviceStatusColumnWidths(spreadsheetId, token, newSheetId)

            Log.d(TAG, "Successfully created tab '$STATUS_SHEET_TITLE' (sheetId=$newSheetId)")
            Result.success(newSheetId)
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring Device_Status tab", e)
            Result.failure(e)
        }
    }

    /**
     * Sets column widths for vertical card layout: Col A (Attribute)=220px, Col B (Status)=280px.
     */
    private fun setDeviceStatusColumnWidths(spreadsheetId: String, token: String, sheetId: Int) {
        try {
            val requests = JSONArray()
            val colWidths = listOf(Pair(0, 220), Pair(1, 280))
            for ((colIndex, width) in colWidths) {
                requests.put(JSONObject().apply {
                    put("updateDimensionProperties", JSONObject().apply {
                        put("range", JSONObject().apply {
                            put("sheetId", sheetId)
                            put("dimension", "COLUMNS")
                            put("startIndex", colIndex)
                            put("endIndex", colIndex + 1)
                        })
                        put("properties", JSONObject().apply {
                            put("pixelSize", width)
                        })
                        put("fields", "pixelSize")
                    })
                })
            }

            val batchUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate"
            val batchReq = Request.Builder()
                .url(batchUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(JSONObject().put("requests", requests).toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(batchReq).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting Device_Status column widths", e)
        }
    }

    /**
     * Appends a vertical diagnostic report card to the 'Device_Status' tab:
     * - Checks if previous data exists. If yes, prepends a 2-row gap.
     * - Top banner row: "DEVICE STATUS REPORT — dd-MM-yyyy hh:mm:ss a" (merged Col A & B, Emerald green).
     * - 11 Attribute rows: Parameter (Col A) | Value (Col B) with zebra styling and borders.
     */
    suspend fun appendDeviceReport(
        context: Context,
        diagnostics: DeviceDiagnostics
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val spreadsheetResult = getOrCreateSpreadsheet(context)
        if (spreadsheetResult.isFailure) {
            return@withContext Result.failure(
                spreadsheetResult.exceptionOrNull() ?: Exception("Unknown error getting spreadsheet")
            )
        }

        var (spreadsheetId, _) = spreadsheetResult.getOrThrow()
        var token = GoogleAuthManager.getAccessToken(context)
            ?: return@withContext Result.failure(Exception("Failed to acquire Google OAuth token"))

        var sheetIdResult = ensureDeviceStatusSheet(spreadsheetId, token)
        if (sheetIdResult.isFailure) {
            return@withContext Result.failure(
                sheetIdResult.exceptionOrNull() ?: Exception("Failed to ensure Device_Status sheet")
            )
        }
        var statusSheetId = sheetIdResult.getOrThrow()

        // 1. Check if sheet has existing reports or legacy horizontal layout
        var hasExistingData = false
        try {
            val checkUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${STATUS_SHEET_TITLE}!A1:B1"
            val checkReq = Request.Builder()
                .url(checkUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            val checkResp = httpClient.newCall(checkReq).execute()
            if (checkResp.isSuccessful) {
                val checkJson = JSONObject(checkResp.body?.string() ?: "{}")
                val vals = checkJson.optJSONArray("values")
                if (vals != null && vals.length() > 0) {
                    val row0 = vals.optJSONArray(0)
                    val a1 = row0?.optString(0, "") ?: ""
                    val b1 = row0?.optString(1, "") ?: ""

                    // If legacy horizontal header was present (B1 == "Battery")
                    if (b1 == "Battery") {
                        Log.d(TAG, "Detected legacy horizontal layout on '$STATUS_SHEET_TITLE'. Clearing for vertical card layout...")
                        val clearUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${STATUS_SHEET_TITLE}!A:Z:clear"
                        val clearReq = Request.Builder()
                            .url(clearUrl)
                            .addHeader("Authorization", "Bearer $token")
                            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                            .build()
                        httpClient.newCall(clearReq).execute().close()
                        hasExistingData = false
                    } else if (a1.isNotBlank()) {
                        hasExistingData = true
                    }
                }
            }
            checkResp.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking existing status data", e)
        }

        val dateTimeFormatter = SimpleDateFormat("dd-MM-yyyy hh:mm:ss a", Locale.getDefault())
        val dateTimeStr = dateTimeFormatter.format(Date(diagnostics.timestamp))

        val attributes = listOf(
            Pair("Date & Time", dateTimeStr),
            Pair("Battery Level", diagnostics.battery),
            Pair("Sound / Ringer", diagnostics.ringer),
            Pair("Do Not Disturb (DND)", diagnostics.dnd),
            Pair("Screen Brightness", diagnostics.brightness),
            Pair("Wi-Fi Status", diagnostics.wifi),
            Pair("Mobile Data", diagnostics.mobileData),
            Pair("Mobile Hotspot", diagnostics.hotspot),
            Pair("Airplane Mode", diagnostics.airplaneMode),
            Pair("Bluetooth", diagnostics.bluetooth),
            Pair("GPS Location", diagnostics.location)
        )

        val rowsArray = JSONArray()

        // 2-row gap before subsequent reports
        if (hasExistingData) {
            rowsArray.put(JSONArray().apply { put(""); put("") })
            rowsArray.put(JSONArray().apply { put(""); put("") })
        }

        // Header Banner row
        rowsArray.put(JSONArray().apply {
            put("DEVICE STATUS REPORT — $dateTimeStr")
            put("")
        })

        // 11 Attribute rows
        for (attr in attributes) {
            rowsArray.put(JSONArray().apply {
                put(attr.first)
                put(attr.second)
            })
        }

        val payload = JSONObject().apply {
            put("range", "$STATUS_SHEET_TITLE!A:B")
            put("majorDimension", "ROWS")
            put("values", rowsArray)
        }

        val appendUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${STATUS_SHEET_TITLE}!A:B:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"

        var request = Request.Builder()
            .url(appendUrl)
            .addHeader("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        var response = httpClient.newCall(request).execute()

        // If 401 Unauthorized, refresh token and retry
        if (response.code == 401) {
            response.close()
            GoogleAuthManager.invalidateToken(context, token)
            token = GoogleAuthManager.getAccessToken(context)
                ?: return@withContext Result.failure(Exception("Re-auth failed during report append"))

            request = Request.Builder()
                .url(appendUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            response = httpClient.newCall(request).execute()
        }

        // Self-Healing: If 404 or 400
        if (response.code == 404 || response.code == 400) {
            response.close()
            Log.w(TAG, "Spreadsheet or status tab missing. Retrying after self-healing...")
            val helper = PreferencesHelper(context)
            helper.spreadsheetId = null
            helper.spreadsheetUrl = null

            val recoveredResult = getOrCreateSpreadsheet(context)
            if (recoveredResult.isSuccess) {
                val (newSpreadsheetId, _) = recoveredResult.getOrThrow()
                spreadsheetId = newSpreadsheetId

                sheetIdResult = ensureDeviceStatusSheet(newSpreadsheetId, token)
                statusSheetId = sheetIdResult.getOrDefault(0)

                val retryUrl = "https://sheets.googleapis.com/v4/spreadsheets/$newSpreadsheetId/values/${STATUS_SHEET_TITLE}!A:B:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"
                val retryReq = Request.Builder()
                    .url(retryUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                response = httpClient.newCall(retryReq).execute()
            } else {
                return@withContext Result.failure(
                    recoveredResult.exceptionOrNull() ?: Exception("Self-healing failed to re-create spreadsheet")
                )
            }
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            response.close()
            return@withContext Result.failure(Exception("Failed to append device report: ${response.code} - $errorBody"))
        }

        val appendRespJson = JSONObject(response.body?.string() ?: "{}")
        response.close()

        // Apply card formatting (Banner merge, Emerald styling, zebra rows, elegant card borders)
        val updatesObj = appendRespJson.optJSONObject("updates")
        val updatedRange = updatesObj?.optString("updatedRange", "") ?: ""
        applyStatusCardFormatting(spreadsheetId, token, updatedRange, statusSheetId, hasExistingData)

        PreferencesHelper(context).lastReportTimestamp = System.currentTimeMillis()
        Log.d(TAG, "Vertical device diagnostics report card successfully appended to Google Sheet")
        Result.success(true)
    }

    /**
     * Styles the vertical report card:
     * - Merges and styles banner header across Col A & B in Emerald Green.
     * - Alternating zebra striping on attribute rows.
     * - Elegant border framing the report card.
     */
    private fun applyStatusCardFormatting(
        spreadsheetId: String,
        token: String,
        updatedRange: String,
        sheetTabId: Int,
        hasGap: Boolean
    ) {
        if (updatedRange.isBlank()) return
        try {
            val match = Regex("""!A(\d+):B(\d+)""").find(updatedRange) ?: return
            val startRow = match.groupValues[1].toInt() - 1 // 0-indexed inclusive
            val endRow = match.groupValues[2].toInt()

            val cardStartRow = if (hasGap) startRow + 2 else startRow
            val cardEndRow = endRow
            val bannerRow = cardStartRow

            val requests = JSONArray()

            // 1. Merge Header Banner across Column A and Column B
            requests.put(JSONObject().apply {
                put("mergeCells", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetTabId)
                        put("startRowIndex", bannerRow)
                        put("endRowIndex", bannerRow + 1)
                        put("startColumnIndex", 0)
                        put("endColumnIndex", 2)
                    })
                    put("mergeType", "MERGE_ALL")
                })
            })

            // 2. Style Header Banner (Forest Green background, bold white text, centered)
            requests.put(JSONObject().apply {
                put("repeatCell", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetTabId)
                        put("startRowIndex", bannerRow)
                        put("endRowIndex", bannerRow + 1)
                        put("startColumnIndex", 0)
                        put("endColumnIndex", 2)
                    })
                    put("cell", JSONObject().apply {
                        put("userEnteredFormat", JSONObject().apply {
                            put("backgroundColor", JSONObject().apply {
                                put("red", 0.082f)
                                put("green", 0.447f)
                                put("blue", 0.259f) // Forest green
                            })
                            put("textFormat", JSONObject().apply {
                                put("bold", true)
                                put("fontSize", 11)
                                put("foregroundColor", JSONObject().apply {
                                    put("red", 1.0f)
                                    put("green", 1.0f)
                                    put("blue", 1.0f)
                                })
                            })
                            put("horizontalAlignment", "CENTER")
                            put("verticalAlignment", "MIDDLE")
                        })
                    })
                    put("fields", "userEnteredFormat(backgroundColor,textFormat,horizontalAlignment,verticalAlignment)")
                })
            })

            // 3. Style Attribute Rows with subtle alternating zebra striping
            for (rowIndex in (bannerRow + 1) until cardEndRow) {
                val isEven = (rowIndex - bannerRow) % 2 == 1
                val bgColor = if (isEven) {
                    JSONObject().apply {
                        put("red", 0.96f)
                        put("green", 0.97f)
                        put("blue", 0.98f) // Very light slate tint
                    }
                } else {
                    JSONObject().apply {
                        put("red", 1.0f)
                        put("green", 1.0f)
                        put("blue", 1.0f) // Crisp white
                    }
                }

                // Col A (Attribute Name)
                requests.put(JSONObject().apply {
                    put("repeatCell", JSONObject().apply {
                        put("range", JSONObject().apply {
                            put("sheetId", sheetTabId)
                            put("startRowIndex", rowIndex)
                            put("endRowIndex", rowIndex + 1)
                            put("startColumnIndex", 0)
                            put("endColumnIndex", 1)
                        })
                        put("cell", JSONObject().apply {
                            put("userEnteredFormat", JSONObject().apply {
                                put("backgroundColor", bgColor)
                                put("textFormat", JSONObject().apply {
                                    put("bold", true)
                                    put("fontSize", 10)
                                    put("foregroundColor", JSONObject().apply {
                                        put("red", 0.15f)
                                        put("green", 0.20f)
                                        put("blue", 0.25f)
                                    })
                                })
                                put("horizontalAlignment", "LEFT")
                                put("verticalAlignment", "MIDDLE")
                            })
                        })
                        put("fields", "userEnteredFormat(backgroundColor,textFormat,horizontalAlignment,verticalAlignment)")
                    })
                })

                // Col B (Status Value)
                requests.put(JSONObject().apply {
                    put("repeatCell", JSONObject().apply {
                        put("range", JSONObject().apply {
                            put("sheetId", sheetTabId)
                            put("startRowIndex", rowIndex)
                            put("endRowIndex", rowIndex + 1)
                            put("startColumnIndex", 1)
                            put("endColumnIndex", 2)
                        })
                        put("cell", JSONObject().apply {
                            put("userEnteredFormat", JSONObject().apply {
                                put("backgroundColor", bgColor)
                                put("textFormat", JSONObject().apply {
                                    put("bold", false)
                                    put("fontSize", 10)
                                    put("foregroundColor", JSONObject().apply {
                                        put("red", 0.08f)
                                        put("green", 0.10f)
                                        put("blue", 0.14f)
                                    })
                                })
                                put("horizontalAlignment", "LEFT")
                                put("verticalAlignment", "MIDDLE")
                            })
                        })
                        put("fields", "userEnteredFormat(backgroundColor,textFormat,horizontalAlignment,verticalAlignment)")
                    })
                })
            }

            // 4. Card Borders
            requests.put(JSONObject().apply {
                put("updateBorders", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetTabId)
                        put("startRowIndex", bannerRow)
                        put("endRowIndex", cardEndRow)
                        put("startColumnIndex", 0)
                        put("endColumnIndex", 2)
                    })
                    put("top", JSONObject().apply {
                        put("style", "SOLID_MEDIUM")
                        put("color", JSONObject().apply {
                            put("red", 0.082f)
                            put("green", 0.447f)
                            put("blue", 0.259f)
                        })
                    })
                    put("bottom", JSONObject().apply {
                        put("style", "SOLID")
                        put("color", JSONObject().apply {
                            put("red", 0.75f)
                            put("green", 0.80f)
                            put("blue", 0.85f)
                        })
                    })
                    put("left", JSONObject().apply {
                        put("style", "SOLID")
                        put("color", JSONObject().apply {
                            put("red", 0.75f)
                            put("green", 0.80f)
                            put("blue", 0.85f)
                        })
                    })
                    put("right", JSONObject().apply {
                        put("style", "SOLID")
                        put("color", JSONObject().apply {
                            put("red", 0.75f)
                            put("green", 0.80f)
                            put("blue", 0.85f)
                        })
                    })
                    put("innerHorizontal", JSONObject().apply {
                        put("style", "SOLID")
                        put("color", JSONObject().apply {
                            put("red", 0.88f)
                            put("green", 0.90f)
                            put("blue", 0.92f)
                        })
                    })
                    put("innerVertical", JSONObject().apply {
                        put("style", "SOLID")
                        put("color", JSONObject().apply {
                            put("red", 0.88f)
                            put("green", 0.90f)
                            put("blue", 0.92f)
                        })
                    })
                })
            })

            val batchUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate"
            val batchReq = Request.Builder()
                .url(batchUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(JSONObject().put("requests", requests).toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(batchReq).execute().close()
            Log.d(TAG, "Applied vertical card formatting for rows $bannerRow to $cardEndRow")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying vertical status card formatting", e)
        }
    }
}
