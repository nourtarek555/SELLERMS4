package com.example.Seller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "seller_notifications"
        private const val CHANNEL_NAME = "Seller Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications for new orders and important updates"
        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_STOCK_ALERT_PREFIX = "stock_alert_"
        private const val KEY_LAST_ORDER_COUNT = "last_order_count"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNewOrderNotification(orderCount: Int) {
        // Only show notification if order count increased
        val lastOrderCount = prefs.getInt(KEY_LAST_ORDER_COUNT, 0)
        if (orderCount <= lastOrderCount) {
            // Order count didn't increase, don't show notification
            return
        }
        
        // Update the last order count
        prefs.edit().putInt(KEY_LAST_ORDER_COUNT, orderCount).apply()
        
        val intent = Intent(context, OrdersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Order${if (orderCount > 1) "s" else ""} Received!")
            .setContentText("You have $orderCount pending order${if (orderCount > 1) "s" else ""} waiting for your approval")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("You have $orderCount pending order${if (orderCount > 1) "s" else ""} waiting for your approval. Tap to view orders."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }

    fun showOrderStatusUpdateNotification(orderId: String, status: String) {
        val intent = Intent(context, OrdersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, message) = when (status.lowercase()) {
            "ready" -> "Order Completed" to "Order is ready for pickup - Order: ${orderId.take(12)}..."
            "delivered" -> "Order Completed" to "Order has been delivered - Order: ${orderId.take(12)}..."
            else -> "Order Status Updated" to "Order status updated - Order: ${orderId.take(12)}..."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showStockAlertNotification(productName: String, stockLevel: Int, productId: String? = null) {
        // Use productId if available, otherwise use productName as key
        val alertKey = KEY_STOCK_ALERT_PREFIX + (productId ?: productName)
        
        // Check if we've already sent a notification for this product
        if (prefs.getBoolean(alertKey, false)) {
            // Already sent notification for this product, skip
            return
        }
        
        // Mark that we've sent the notification
        prefs.edit().putBoolean(alertKey, true).apply()
        
        val intent = Intent(context, ShopActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Low Stock Alert")
            .setContentText("$productName is running low! Only $stockLevel left in stock.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$productName is running low! Only $stockLevel item${if (stockLevel != 1) "s" else ""} left in stock. Consider restocking soon."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    /**
     * Clear the low stock alert flag for a product (call when stock goes back above threshold)
     */
    fun clearStockAlert(productId: String? = null, productName: String? = null) {
        val alertKey = KEY_STOCK_ALERT_PREFIX + (productId ?: productName ?: return)
        prefs.edit().remove(alertKey).apply()
    }
}

