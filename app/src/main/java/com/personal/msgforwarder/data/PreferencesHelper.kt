package com.personal.msgforwarder.data

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences wrapper for all local app state and Google integration settings.
 */
class PreferencesHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("msg_backup_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_BACKUP_ENABLED = "is_backup_enabled"
        private const val KEY_GOOGLE_ACCOUNT_EMAIL = "google_account_email"
        private const val KEY_GOOGLE_ACCOUNT_NAME = "google_account_name"
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_SPREADSHEET_URL = "spreadsheet_url"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_TOTAL_SYNCED_COUNT = "total_synced_count"
    }

    var totalSyncedCount: Int
        get() = prefs.getInt(KEY_TOTAL_SYNCED_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_SYNCED_COUNT, value).apply()

    var isBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_IS_BACKUP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_BACKUP_ENABLED, value).apply()

    var googleAccountEmail: String?
        get() = prefs.getString(KEY_GOOGLE_ACCOUNT_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_GOOGLE_ACCOUNT_EMAIL, value).apply()

    var googleAccountName: String?
        get() = prefs.getString(KEY_GOOGLE_ACCOUNT_NAME, null)
        set(value) = prefs.edit().putString(KEY_GOOGLE_ACCOUNT_NAME, value).apply()

    var spreadsheetId: String?
        get() = prefs.getString(KEY_SPREADSHEET_ID, null)
        set(value) = prefs.edit().putString(KEY_SPREADSHEET_ID, value).apply()

    var spreadsheetUrl: String?
        get() = prefs.getString(KEY_SPREADSHEET_URL, null)
        set(value) = prefs.edit().putString(KEY_SPREADSHEET_URL, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, value).apply()

    val isSignedIn: Boolean
        get() = !googleAccountEmail.isNullOrBlank()

    fun clearGoogleAccount() {
        prefs.edit()
            .remove(KEY_GOOGLE_ACCOUNT_EMAIL)
            .remove(KEY_GOOGLE_ACCOUNT_NAME)
            .remove(KEY_SPREADSHEET_ID)
            .remove(KEY_SPREADSHEET_URL)
            .apply()
    }

    fun clearAll() = prefs.edit().clear().apply()
}
