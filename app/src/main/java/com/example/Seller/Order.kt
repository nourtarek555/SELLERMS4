// The package name for the seller application.
package com.example.Seller

// Import for Serializable interface, which allows objects to be passed between activities or stored.
import java.io.Serializable

/**
 * A data class representing an order.
 * This class holds all the information related to a customer's order.
 * It implements Serializable to allow it to be passed between components, for example, in an Intent.
 */
data class Order(
    // The unique ID of the order.
    var orderId: String? = null,
    // The ID of the seller fulfilling the order.
    var sellerId: String? = null,
    // The ID of the buyer who placed the order.
    var buyerId: String? = null,
    // The name of the buyer.
    var buyerName: String? = null,
    // The address of the buyer.
    var buyerAddress: String? = null,
    // The email of the buyer.
    var buyerEmail: String? = null,
    // The phone number of the buyer.
    var buyerPhone: String? = null,
    // The current status of the order (e.g., "pending", "accepted", "preparing", "ready", "delivered", "rejected").
    var status: String? = null,
    // The total amount of the order.
    var totalAmount: Double? = null,
    // The type of delivery (e.g., "pickup" or "delivery").
    var deliveryType: String? = null,
    // The full delivery address if the delivery type is "delivery".
    var deliveryAddress: String? = null,
    // The price of the delivery.
    var deliveryPrice: Double? = null,
    // The timestamp of when the order was placed.
    var timestamp: Long? = null,
    // A list of the items included in the order.
    var items: MutableList<OrderItem>? = null
) : Serializable // Implementing Serializable allows this object to be passed in Intents.

/**
 * A data class representing an item within an order.
 * This class holds information about a single product in an order.
 * It also implements Serializable.
 */
data class OrderItem(
    // The unique ID of the product.
    var productId: String? = null,
    // The name of the product.
    var productName: String? = null,
    // The quantity of the product ordered.
    var quantity: Int? = null,
    // The price of a single unit of the product.
    var price: Double? = null,
    // The URL of the product's photo.
    var photoUrl: String? = null,
    // An alternative field for the image URL.
    var imageUrl: String? = null,
    // The maximum stock of the product.
    var maxStock: Int? = null,
    // The ID of the seller of the product. This is included in each item for cases where an order might contain items from multiple sellers.
    var sellerId: String? = null,
    // The total price for this order item (quantity * price).
    var totalPrice: Double? = null
) : Serializable // Implementing Serializable allows this object to be passed in Intents.
