package com.personal.msgforwarder.data.google

import android.content.Context
import android.util.Log
import com.personal.msgforwarder.data.MessageData
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.util.CryptoHelper
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
 *   automatically re-creates 'msg_backup' on the fly with headers and merged layout,
 *   retrying the sync immediately without dropping messages.
 *
 * Sheet Structure:
 * - Column A (Index 0): Date (dd-MM-yyyy)
 * - Column B (Index 1): Time (hh:mm:ss a)
 * - Column C (Index 2): Sender
 * - Columns D to CW (Index 3 to 103): 100 merged cells for Full Message Content
 */
object GoogleSheetsManager {

    private const val TAG = "GoogleSheetsManager"
    private const val SPREADSHEET_NAME = "msg_backup"
    private const val SHEET_TITLE = "Messages"
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

    data class SheetMetadata(val sheetId: Int, val columnCount: Int, val isValid: Boolean)

    /**
     * Resolves the spreadsheet ID and webViewUrl for 'msg_backup'.
     * Searches Google Drive first, verifies existing files, and self-heals if deleted.
     */
    suspend fun getOrCreateSpreadsheet(context: Context): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val prefs = PreferencesHelper(context)
            val cachedId = prefs.spreadsheetId
            val cachedUrl = prefs.spreadsheetUrl

            var token = GoogleAuthManager.getAccessToken(context)
                ?: return@withContext Result.failure(Exception("Failed to acquire Google OAuth token"))

            if (!cachedId.isNullOrBlank() && !cachedUrl.isNullOrBlank()) {
                val metadata = getSheetMetadata(cachedId, token)
                if (metadata.isValid) {
                    ensureSheetFormatted(cachedId, token, metadata)
                    return@withContext Result.success(Pair(cachedId, cachedUrl))
                } else {
                    Log.w(TAG, "Cached spreadsheet $cachedId is invalid/deleted. Self-healing...")
                    prefs.spreadsheetId = null
                    prefs.spreadsheetUrl = null
                }
            }

            // 1. Search Google Drive for existing 'msg_backup'
            val searchQuery = "name = '$SPREADSHEET_NAME' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
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
                ensureSheetFormatted(id, token, metadata)

                return@withContext Result.success(Pair(id, url))
            }

            // 2. If not found in Drive, create a fresh Google Spreadsheet with 110 columns
            Log.d(TAG, "Creating fresh spreadsheet: $SPREADSHEET_NAME")
            val createJson = JSONObject().apply {
                put("properties", JSONObject().apply {
                    put("title", SPREADSHEET_NAME)
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

            // Format header row and column widths
            val metadata = SheetMetadata(sheetId = 0, columnCount = REQUIRED_COLUMNS, isValid = true)
            ensureSheetFormatted(spreadsheetId, token, metadata)

            Log.d(TAG, "Created spreadsheet $spreadsheetId successfully with self-healing headers")
            Result.success(Pair(spreadsheetId, spreadsheetUrl))
        }

    /**
     * Inspects spreadsheet to get sheetId and ensure grid has at least 110 columns.
     */
    private fun getSheetMetadata(spreadsheetId: String, token: String): SheetMetadata {
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=sheets(properties(sheetId,title,gridProperties))"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            var sheetId = 0
            var colCount = 26
            val isSuccess = response.isSuccessful

            if (isSuccess) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val sheets = json.optJSONArray("sheets")
                if (sheets != null && sheets.length() > 0) {
                    for (i in 0 until sheets.length()) {
                        val props = sheets.getJSONObject(i).getJSONObject("properties")
                        val title = props.optString("title", "")
                        if (title == SHEET_TITLE || i == 0) {
                            sheetId = props.optInt("sheetId", 0)
                            val grid = props.optJSONObject("gridProperties")
                            colCount = grid?.optInt("columnCount", 26) ?: 26
                            break
                        }
                    }
                }
            }
            response.close()
            SheetMetadata(sheetId, colCount, isValid = isSuccess)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching sheet metadata", e)
            SheetMetadata(0, 26, isValid = false)
        }
    }

    /**
     * Applies styling, column expansion (110 cols), header merge, and custom widths to the sheet.
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

            val batchUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate"
            val batchReq = Request.Builder()
                .url(batchUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(JSONObject().put("requests", requests).toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(batchReq).execute().close()
            Log.d(TAG, "Header and sheet styling applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying sheet formatting", e)
        }
    }

    /**
     * Appends a list of messages to the 'msg_backup' spreadsheet:
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

                val prefs = PreferencesHelper(context)
                prefs.spreadsheetId = null
                prefs.spreadsheetUrl = null

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

            applyRowFormatting(spreadsheetId, token, updatedRange)

            PreferencesHelper(context).lastSyncTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Successfully appended ${messages.size} message(s) to Google Sheet")
            Result.success(messages.size)
        }

    /**
     * Merges 100 cells horizontally (columns D to CW) for each appended row,
     * enables text wrapping, aligns date/time to center, and aligns message to top.
     */
    private fun applyRowFormatting(spreadsheetId: String, token: String, updatedRange: String) {
        if (updatedRange.isBlank()) return

        try {
            // Match row numbers in range, e.g. "!A2:D5" -> start: 2, end: 5
            val match = Regex("""!A(\d+):D(\d+)""").find(updatedRange) ?: return
            val startRow = match.groupValues[1].toInt() - 1 // 0-indexed inclusive
            val endRow = match.groupValues[2].toInt()       // 0-indexed exclusive

            val metadata = getSheetMetadata(spreadsheetId, token)
            val sheetId = metadata.sheetId

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

            // 2. Format Date, Time, Sender columns: center horizontal, top vertical
            requests.put(JSONObject().apply {
                put("repeatCell", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("startRowIndex", startRow)
                        put("endRowIndex", endRow)
                        put("startColumnIndex", 0)
                        put("endColumnIndex", 3)
                    })
                    put("cell", JSONObject().apply {
                        put("userEnteredFormat", JSONObject().apply {
                            put("horizontalAlignment", "CENTER")
                            put("verticalAlignment", "TOP")
                        })
                    })
                    put("fields", "userEnteredFormat(horizontalAlignment,verticalAlignment)")
                })
            })

            // 3. Format Message cell: wrap text, left horizontal, top vertical
            requests.put(JSONObject().apply {
                put("repeatCell", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("startRowIndex", startRow)
                        put("endRowIndex", endRow)
                        put("startColumnIndex", MSG_START_COL)
                        put("endColumnIndex", MSG_END_COL)
                    })
                    put("cell", JSONObject().apply {
                        put("userEnteredFormat", JSONObject().apply {
                            put("wrapStrategy", "WRAP")
                            put("horizontalAlignment", "LEFT")
                            put("verticalAlignment", "TOP")
                        })
                    })
                    put("fields", "userEnteredFormat(wrapStrategy,horizontalAlignment,verticalAlignment)")
                })
            })

            val batchUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate"
            val batchReq = Request.Builder()
                .url(batchUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(JSONObject().put("requests", requests).toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(batchReq).execute().close()
            Log.d(TAG, "Applied row merge and formatting for rows $startRow to $endRow")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying row merge formatting", e)
        }
    }
}
