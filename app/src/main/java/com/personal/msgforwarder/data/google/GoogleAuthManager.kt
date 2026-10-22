package com.personal.msgforwarder.data.google

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.personal.msgforwarder.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages Google Sign-In and OAuth token lifecycle with Drive and Sheets scopes.
 * Self-healing: Automatically triggers high-priority recovery notification if user consent is needed.
 */
object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"

    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val SCOPE_SPREADSHEETS = "https://www.googleapis.com/auth/spreadsheets"

    private val OAUTH_SCOPE_STRING = "oauth2:$SCOPE_DRIVE_FILE $SCOPE_SPREADSHEETS"

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_DRIVE_FILE), Scope(SCOPE_SPREADSHEETS))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(context: Context): Intent {
        return getGoogleSignInClient(context).signInIntent
    }

    fun parseSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                Result.success(account)
            } else {
                Result.failure(Exception("Google Sign-In returned null account"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in parseSignInResult", e)
            Result.failure(e)
        }
    }

    /**
     * Gets a valid OAuth 2.0 Bearer access token for background network requests.
     * If user intervention or re-consent is required, fires an actionable notification.
     */
    suspend fun getAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val signedInAccount = GoogleSignIn.getLastSignedInAccount(context)
        val androidAccount = signedInAccount?.account
        if (androidAccount == null) {
            Log.w(TAG, "No signed-in Google account found")
            return@withContext null
        }

        try {
            GoogleAuthUtil.getToken(context, androidAccount, OAUTH_SCOPE_STRING)
        } catch (e: UserRecoverableAuthException) {
            Log.e(TAG, "User interaction needed for Google OAuth token", e)
            NotificationHelper.showAuthRecoveryNotification(context, e.intent)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching OAuth access token", e)
            null
        }
    }

    /**
     * Clears an invalidated or expired token from local cache so the next call acquires a fresh one.
     */
    suspend fun invalidateToken(context: Context, token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
            Log.d(TAG, "OAuth token cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error invalidating OAuth token", e)
        }
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        try {
            getGoogleSignInClient(context).signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Error during signOut", e)
        }
    }
}
