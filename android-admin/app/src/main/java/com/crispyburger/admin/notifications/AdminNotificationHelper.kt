package com.crispyburger.admin.notifications

import android.Manifest
import android.annotation.SuppressLint
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
import com.crispyburger.admin.R

object AdminNotificationHelper {
    const val ORDERS_CHANNEL_ID = "cbms_admin_orders"
    const val PAYMENTS_CHANNEL_ID = "cbms_admin_payments"
    const val EXTRA_ORDER_ID = "cbms_order_id"

    private const val PREFS_NAME = "cbms_admin_notification_state"
    // New key: the old "recent_event_keys" was a StringSet and reading it as a String would crash.
    private const val RECENT_EVENTS_KEY = "recent_events_v2"

    fun createChannel(context: Context) {
        createChannels(context)
    }

    fun post(
        context: Context,
        eventKey: String,
        title: String,
        body: String,
        isPayment: Boolean = eventKey.startsWith("payment:"),
        orderId: String? = null
    ) {
        showOrderEvent(
            context = context,
            eventKey = eventKey,
            title = title,
            message = body,
            isPayment = isPayment,
            orderId = orderId
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

    @SuppressLint("MissingPermission") // checked explicitly below
    fun showOrderEvent(
        context: Context,
        eventKey: String,
        title: String,
        message: String,
        isPayment: Boolean,
        orderId: String? = null
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
            if (!orderId.isNullOrBlank()) putExtra(EXTRA_ORDER_ID, orderId)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            eventKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (isPayment) PAYMENTS_CHANNEL_ID else ORDERS_CHANNEL_ID
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_cbms_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(if (isPayment) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()

        // The unique event key is the notification tag, so two events can never share an id.
        NotificationManagerCompat.from(appContext).notify(eventKey, 0, notification)
    }

    @Synchronized
    private fun rememberEvent(context: Context, eventKey: String): Boolean {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = preferences.getString(RECENT_EVENTS_KEY, "").orEmpty()
        val result = EventDedupe.check(stored, eventKey, System.currentTimeMillis())
        if (result.shouldPost) {
            preferences.edit().putString(RECENT_EVENTS_KEY, result.updatedState).apply()
        }
        return result.shouldPost
    }
}
