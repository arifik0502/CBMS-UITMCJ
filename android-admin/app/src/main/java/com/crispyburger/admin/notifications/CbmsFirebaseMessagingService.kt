package com.crispyburger.admin.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CbmsFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FcmTokenManager.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"]?.lowercase()?.take(32) ?: "order"
        val eventId = data["event_id"]?.take(120)
            ?: message.messageId?.take(120)
            ?: System.currentTimeMillis().toString()

        val isPayment = type == "payment" || type.startsWith("payment_")
        val eventKey = when {
            isPayment -> "payment:$eventId"
            type.startsWith("inventory") -> "inventory:$eventId"
            else -> "order:$eventId"
        }

        val title = (data["title"] ?: message.notification?.title)
            ?.take(80)
            ?.ifBlank { null }
            ?: if (isPayment) "CBMS payment update" else "CBMS order update"

        val body = (data["body"] ?: message.notification?.body)
            ?.take(240)
            ?.ifBlank { null }
            ?: "There is a new update in your CBMS admin workspace."

        AdminNotificationHelper.createChannels(applicationContext)
        AdminNotificationHelper.showOrderEvent(
            context = applicationContext,
            eventKey = eventKey,
            title = title,
            message = body,
            isPayment = isPayment
        )
    }
}
