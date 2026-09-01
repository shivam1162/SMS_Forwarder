package com.personal.msgforwarder.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.personal.msgforwarder.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * Checks Firebase Realtime Database at /app_update for a newer APK version.
     */
    fun checkForUpdates(onResult: (AppUpdateInfo?) -> Unit) {
        val database = FirebaseDatabase.getInstance()
        val updateRef = database.getReference("app_update")

        updateRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val updateInfo = snapshot.getValue(AppUpdateInfo::class.java)
                    if (updateInfo != null && updateInfo.versionCode > BuildConfig.VERSION_CODE && updateInfo.downloadUrl.isNotBlank()) {
                        Log.d(TAG, "New version available: ${updateInfo.versionName} (${updateInfo.versionCode}) vs current ${BuildConfig.VERSION_CODE}")
                        onResult(updateInfo)
                    } else {
                        Log.d(TAG, "App is up to date (version ${BuildConfig.VERSION_CODE})")
                        onResult(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing update info", e)
                    onResult(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check for updates: ${error.message}")
                onResult(null)
            }
        })
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
