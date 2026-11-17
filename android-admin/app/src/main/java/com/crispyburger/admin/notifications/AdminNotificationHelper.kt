package com.crispyburger.admin.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.crispyburger.admin.MainActivity

object AdminNotificationHelper {
    const val ORDERS_CHANNEL_ID = "cbms_admin_orders"
    const val PAYMENTS_CHANNEL_ID = "cbms_admin_payments"
    private const val PREFS_NAME = "cbms_admin_notification_state"
    private const val RECENT_EVENTS_KEY = "recent_event_keys"
    private const val MAX_RECENT_EVENTS = 250

    fun createChannel(context: Context) {
        createChannels(context)
    }

    fun post(
        context: Context,
        eventKey: String,
        title: String,
        body: String
    ) {
        showOrderEvent(
            context = context,
            eventKey = eventKey,
            title = title,
            message = body,
            isPayment = eventKey.startsWith("payment:")
        )
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val orderChannel = NotificationChannel(
            ORDERS_CHANNEL_ID,
            "Order updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "New orders and order status changes"
            enableVibration(true)
            setSound(sound, audioAttributes)
        }

        val paymentChannel = NotificationChannel(
            PAYMENTS_CHANNEL_ID,
            "Payment updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "DuitNow receipt verification and cash collection updates"
            enableVibration(true)
            setSound(sound, audioAttributes)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(listOf(orderChannel, paymentChannel))
    }

    fun showOrderEvent(
        context: Context,
        eventKey: String,
        title: String,
        message: String,
        isPayment: Boolean
    ) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (!rememberEvent(appContext, eventKey)) return

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            eventKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (isPayment) PAYMENTS_CHANNEL_ID else ORDERS_CHANNEL_ID
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(if (isPayment) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()

        NotificationManagerCompat.from(appContext).notify(eventKey.hashCode(), notification)
    }

    private fun rememberEvent(context: Context, eventKey: String): Boolean {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recent = LinkedHashSet(
            preferences.getStringSet(RECENT_EVENTS_KEY, emptySet()).orEmpty()
        )
        if (!recent.add(eventKey)) return false

        while (recent.size > MAX_RECENT_EVENTS) {
            val oldest = recent.firstOrNull() ?: break
            recent.remove(oldest)
        }

        preferences.edit().putStringSet(RECENT_EVENTS_KEY, recent).apply()
        return true
    }
}
