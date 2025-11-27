// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes related to notifications.
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
// Import for NotificationCompat, a class for building notifications with backward compatibility.
import androidx.core.app.NotificationCompat

/**
 * A helper class for creating and managing notifications in the seller app.
 * This class handles the creation of notification channels and provides methods to show different types of notifications,
 * such as new order alerts, order status updates, and low stock warnings.
 * @param context The application context.
 */
class NotificationHelper(private val context: Context) {

    // Companion object to hold constants for the class.
    companion object {
        // The ID of the notification channel.
        private const val CHANNEL_ID = "seller_notifications"
        // The user-visible name of the notification channel.
        private const val CHANNEL_NAME = "Seller Notifications"
        // The user-visible description of the notification channel.
        private const val CHANNEL_DESCRIPTION = "Notifications for new orders and important updates"
        // The name of the SharedPreferences file for storing notification-related data.
        private const val PREFS_NAME = "notification_prefs"
        // The prefix for keys used to store stock alert flags in SharedPreferences.
        private const val KEY_STOCK_ALERT_PREFIX = "stock_alert_"
        // The key for storing the last known order count in SharedPreferences.
        private const val KEY_LAST_ORDER_COUNT = "last_order_count"
    }

    // The NotificationManager system service for managing notifications.
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    // SharedPreferences for storing notification-related data.
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // The init block is called when the class is instantiated.
    init {
        // Create the notification channel.
        createNotificationChannel()
    }

    /**
     * Creates a notification channel for the app.
     * This is required on Android 8.0 (API level 26) and higher.
     * The channel is configured with a name, description, and importance level.
     */
    private fun createNotificationChannel() {
        // Check if the Android version is Oreo or higher.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create a NotificationChannel with high importance.
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                // Set the channel description.
                description = CHANNEL_DESCRIPTION
                // Enable vibration for notifications on this channel.
                enableVibration(true)
                // Enable lights for notifications on this channel.
                enableLights(true)
            }
            // Create the notification channel using the NotificationManager.
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Shows a notification for new orders.
     * This notification is only shown if the number of orders has increased since the last check.
     * @param orderCount The current number of pending orders.
     */
    fun showNewOrderNotification(orderCount: Int) {
        // Get the last known order count from SharedPreferences.
        val lastOrderCount = prefs.getInt(KEY_LAST_ORDER_COUNT, 0)
        // If the order count has not increased, do not show a notification.
        if (orderCount <= lastOrderCount) {
            return
        }
        
        // Update the last known order count in SharedPreferences.
        prefs.edit().putInt(KEY_LAST_ORDER_COUNT, orderCount).apply()
        
        // Create an intent to open the OrdersActivity when the notification is tapped.
        val intent = Intent(context, OrdersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // Create a PendingIntent for the intent.
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification using NotificationCompat.Builder.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Set the small icon for the notification.
            .setContentTitle("New Order${if (orderCount > 1) "s" else ""} Received!") // Set the title.
            .setContentText("You have $orderCount pending order${if (orderCount > 1) "s" else ""} waiting for your approval") // Set the content text.
            .setStyle(NotificationCompat.BigTextStyle() // Use a BigTextStyle to show more text when the notification is expanded.
                .bigText("You have $orderCount pending order${if (orderCount > 1) "s" else ""} waiting for your approval. Tap to view orders."))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Set the priority to high.
            .setContentIntent(pendingIntent) // Set the PendingIntent to be fired when the notification is tapped.
            .setAutoCancel(true) // Automatically dismiss the notification when it is tapped.
            .build()

        // Show the notification.
        notificationManager.notify(1, notification)
    }

    /**
     * Shows a notification for an order status update.
     * @param orderId The ID of the order that was updated.
     * @param status The new status of the order.
     */
    fun showOrderStatusUpdateNotification(orderId: String, status: String) {
        // Create an intent to open the OrdersActivity.
        val intent = Intent(context, OrdersActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // Create a PendingIntent.
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Determine the title and message of the notification based on the order status.
        val (title, message) = when (status.lowercase()) {
            "ready" -> "Order Completed" to "Order is ready for pickup - Order: ${orderId.take(12)}..."
            "delivered" -> "Order Completed" to "Order has been delivered - Order: ${orderId.take(12)}..."
            else -> "Order Status Updated" to "Order status updated - Order: ${orderId.take(12)}..."
        }

        // Build the notification.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Show the notification with a unique ID based on the current time.
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Shows a notification for a low stock alert.
     * This notification is only shown once for each product until the stock is replenished and the alert is cleared.
     * @param productName The name of the product with low stock.
     * @param stockLevel The current stock level of the product.
     * @param productId The ID of the product, used for creating a unique alert key.
     */
    fun showStockAlertNotification(productName: String, stockLevel: Int, productId: String? = null) {
        // Create a unique key for the stock alert using the product ID or name.
        val alertKey = KEY_STOCK_ALERT_PREFIX + (productId ?: productName)
        
        // Check if a notification has already been sent for this product.
        if (prefs.getBoolean(alertKey, false)) {
            return // If so, do not send another one.
        }
        
        // Mark that a notification has been sent for this product.
        prefs.edit().putBoolean(alertKey, true).apply()
        
        // Create an intent to open the ShopActivity.
        val intent = Intent(context, ShopActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the low stock alert notification.
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

        // Show the notification.
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    /**
     * Clears the low stock alert flag for a product.
     * This should be called when the stock for the product goes back above the low stock threshold.
     * @param productId The ID of the product.
     * @param productName The name of the product.
     */
    fun clearStockAlert(productId: String? = null, productName: String? = null) {
        // Create the alert key from the product ID or name.
        val alertKey = KEY_STOCK_ALERT_PREFIX + (productId ?: productName ?: return)
        // Remove the alert flag from SharedPreferences.
        prefs.edit().remove(alertKey).apply()
    }
}
