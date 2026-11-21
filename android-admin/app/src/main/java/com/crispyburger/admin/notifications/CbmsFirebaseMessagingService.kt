package com.crispyburger.admin.notifications

import com.crispyburger.admin.data.AdminRealtimeManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CbmsFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FcmTokenManager.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // While the app is open the live (realtime) connection already notifies the owner.
        // Skipping here avoids showing the same event twice.
        if (AdminRealtimeManager.isConnected) return

        val data = message.data
        val type = data["type"]?.lowercase()?.take(32) ?: "order"
        val eventId = data["event_id"]?.take(120)
            ?: message.messageId?.take(120)
            ?: System.currentTimeMillis().toString()
        val orderId = data["order_id"]?.take(64)?.ifBlank { null }

        val isPayment = type == "payment" || type.startsWith("payment_")
        val isTicket = type == "ticket" || type.startsWith("ticket_")
        val eventKey = when {
            isTicket -> "ticket:${data["ticket_id"]?.take(64) ?: eventId}"
            isPayment -> "payment:${orderId ?: eventId}:${data["status"].orEmpty().take(32)}"
            type.startsWith("inventory") -> "inventory:$eventId"
            else -> "order:${orderId ?: eventId}:${data["status"].orEmpty().take(32)}"
        }

        val title = (data["title"] ?: message.notification?.title)
            ?.take(80)
            ?.ifBlank { null }
            ?: if (isTicket) "New support ticket" else if (isPayment) "CBMS payment update" else "CBMS order update"

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
            isPayment = isPayment,
            orderId = orderId,
            openTab = if (isTicket) AdminNotificationHelper.TAB_TICKETS else null
        )
    }
}
