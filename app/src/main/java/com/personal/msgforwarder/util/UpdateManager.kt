package com.personal.msgforwarder.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.personal.msgforwarder.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = ""
)

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/shivam1162/SMS_Forwarder/releases/latest"

    /**
     * Checks GitHub latest release for a newer APK version.
     */
    fun checkForUpdates(onResult: (AppUpdateInfo?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(GITHUB_RELEASES_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "SMS_Backup_Android_App")
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tagName = json.optString("tag_name", "") // e.g. "v1.0.12"
                    val releaseNotes = json.optString("body", "")

                    // Find APK asset
                    var downloadUrl = ""
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    // Extract build/run number from tag if possible, e.g. "v1.0.15" -> 15
                    val tagClean = tagName.removePrefix("v")
                    val versionParts = tagClean.split(".")
                    val remoteBuildNumber = versionParts.lastOrNull()?.toIntOrNull() ?: 0

                    if (remoteBuildNumber > BuildConfig.VERSION_CODE && downloadUrl.isNotBlank()) {
                        Log.d(TAG, "New version found: $tagName (Build $remoteBuildNumber)")
                        withContext(Dispatchers.Main) {
                            onResult(
                                AppUpdateInfo(
                                    versionCode = remoteBuildNumber,
                                    versionName = tagClean,
                                    downloadUrl = downloadUrl,
                                    releaseNotes = releaseNotes
                                )
                            )
                        }
                        return@launch
                    }
                }
                withContext(Dispatchers.Main) { onResult(null) }
            } catch (e: Exception) {
                Log.d(TAG, "Update check failed or no connection: ${e.message}")
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    /**
     * Downloads the APK file from downloadUrl and launches Android Package Installer via FileProvider.
     */
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("Server returned HTTP ${connection.responseCode}"))
            }

            val fileLength = connection.contentLength
            val apkFile = File(context.cacheDir, "update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int

                    while (input.read(data).also { count = it } != -1) {
                        total += count.toLong()
                        if (fileLength > 0) {
                            val progress = total.toFloat() / fileLength.toFloat()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                        output.write(data, 0, count)
                    }
                    output.flush()
                }
            }

            Log.d(TAG, "APK downloaded to ${apkFile.absolutePath} (${apkFile.length()} bytes)")

            // Launch package installer
            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Download and install failed", e)
            Result.failure(e)
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}
