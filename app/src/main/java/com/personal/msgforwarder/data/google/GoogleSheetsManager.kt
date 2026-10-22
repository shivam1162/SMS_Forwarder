package com.personal.msgforwarder.data.google

import android.content.Context
import android.util.Log
import com.personal.msgforwarder.data.MessageData
import com.personal.msgforwarder.data.PreferencesHelper
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
 */
object GoogleSheetsManager {

    private const val TAG = "GoogleSheetsManager"
    private const val SPREADSHEET_NAME = "msg_backup"
    private const val SHEET_TITLE = "Messages"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Resolves the spreadsheet ID and webViewUrl for 'msg_backup'.
     * Searches Google Drive first, and creates it with headers if it doesn't exist.
     */
    suspend fun getOrCreateSpreadsheet(context: Context): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val prefs = PreferencesHelper(context)
            val cachedId = prefs.spreadsheetId
            val cachedUrl = prefs.spreadsheetUrl

            if (!cachedId.isNullOrBlank() && !cachedUrl.isNullOrBlank()) {
                return@withContext Result.success(Pair(cachedId, cachedUrl))
            }

            var token = GoogleAuthManager.getAccessToken(context)
                ?: return@withContext Result.failure(Exception("Failed to acquire Google OAuth token"))

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
                Log.d(TAG, "Found existing spreadsheet: $id ($url)")
                return@withContext Result.success(Pair(id, url))
            }

            // 2. If not found, create a new Google Spreadsheet
            Log.d(TAG, "Creating new spreadsheet: $SPREADSHEET_NAME")
            val createJson = JSONObject().apply {
                put("properties", JSONObject().apply {
                    put("title", SPREADSHEET_NAME)
                })
                put("sheets", JSONArray().apply {
                    put(JSONObject().apply {
                        put("properties", JSONObject().apply {
                            put("title", SHEET_TITLE)
                            put("gridProperties", JSONObject().apply {
                                put("frozenRowCount", 1)
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

            // 3. Write header row to the newly created spreadsheet
            val headerJson = JSONObject().apply {
                put("values", JSONArray().apply {
                    put(JSONArray().apply {
                        put("Timestamp (Epoch)")
                        put("Date & Time")
                        put("Sender")
                        put("Full Message")
                    })
                })
            }

            val headerUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${SHEET_TITLE}!A1:D1?valueInputOption=USER_ENTERED"
            val headerRequest = Request.Builder()
                .url(headerUrl)
                .addHeader("Authorization", "Bearer $token")
                .put(headerJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(headerRequest).execute().close()

            prefs.spreadsheetId = spreadsheetId
            prefs.spreadsheetUrl = spreadsheetUrl
            Log.d(TAG, "Created spreadsheet $spreadsheetId successfully with headers")
            Result.success(Pair(spreadsheetId, spreadsheetUrl))
        }

    /**
     * Appends a list of messages to the 'msg_backup' spreadsheet in a single batch request.
     */
    suspend fun appendMessages(context: Context, messages: List<MessageData>): Result<Int> =
        withContext(Dispatchers.IO) {
            if (messages.isEmpty()) {
                return@withContext Result.success(0)
            }

            val spreadsheetResult = getOrCreateSpreadsheet(context)
            if (spreadsheetResult.isFailure) {
                return@withContext Result.failure(spreadsheetResult.exceptionOrNull() ?: Exception("Unknown error getting spreadsheet"))
            }

            val (spreadsheetId, _) = spreadsheetResult.getOrThrow()
            var token = GoogleAuthManager.getAccessToken(context)
                ?: return@withContext Result.failure(Exception("Failed to acquire Google OAuth token"))

            val rowsArray = JSONArray()
            for (msg in messages) {
                val dateStr = dateFormatter.format(Date(msg.timestamp))
                val row = JSONArray().apply {
                    put(msg.timestamp.toString())
                    put(dateStr)
                    put(msg.sender)
                    put(msg.body)
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

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                response.close()
                return@withContext Result.failure(Exception("Failed to append to Google Sheet: ${response.code} - $errorBody"))
            }

            response.close()
            PreferencesHelper(context).lastSyncTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Successfully appended ${messages.size} message(s) to Google Sheet")
            Result.success(messages.size)
        }
}
