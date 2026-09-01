package com.personal.msgforwarder.data

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences wrapper for all local app state.
 * All reads are instant (local file) — zero battery cost.
 */
class PreferencesHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_ROLE = "role"
        private const val KEY_IS_ACTIVE = "is_active"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_PARTNER_FCM_TOKEN = "partner_fcm_token"

        const val ROLE_SENDER = "sender"
        const val ROLE_RECEIVER = "receiver"
    }

    var pairingCode: String?
        get() = prefs.getString(KEY_PAIRING_CODE, null)
        set(value) = prefs.edit().putString(KEY_PAIRING_CODE, value).apply()

    var role: String?
        get() = prefs.getString(KEY_ROLE, null)
        set(value) = prefs.edit().putString(KEY_ROLE, value).apply()

    var isActive: Boolean
        get() = prefs.getBoolean(KEY_IS_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ACTIVE, value).apply()

    var fcmToken: String?
        get() = prefs.getString(KEY_FCM_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_FCM_TOKEN, value).apply()

    var partnerFcmToken: String?
        get() = prefs.getString(KEY_PARTNER_FCM_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_PARTNER_FCM_TOKEN, value).apply()

    /**
     * Returns true if the user has completed the pairing flow.
     */
    fun isPaired(): Boolean = pairingCode != null && role != null

    /**
     * Clears all saved data (for reset/unpair).
     */
    fun clear() = prefs.edit().clear().apply()
}
