package com.crispyburger.admin.notifications

import android.content.Context
import com.crispyburger.admin.data.AdminSupabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FcmTokenManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerCurrentDevice(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    registerToken(context.applicationContext, task.result)
                }
            }
    }

    private const val PREFS_NAME = "cbms_admin_fcm"
    private const val TOKEN_KEY = "current_device_token"

    fun currentToken(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TOKEN_KEY, null)
    }

    fun registerToken(context: Context, token: String) {
        if (token.isBlank()) return

        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(TOKEN_KEY, token)
            .apply()

        scope.launch {
            runCatching {
                AdminSupabase.registerDeviceToken(token)
            }
        }
    }
}
