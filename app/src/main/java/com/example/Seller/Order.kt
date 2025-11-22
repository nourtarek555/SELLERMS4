package com.example.Seller

import java.io.Serializable

data class Order(
    var orderId: String? = null,
    var sellerId: String? = null,
    var buyerId: String? = null,
    var buyerName: String? = null,
    var buyerAddress: String? = null,
    var buyerEmail: String? = null,
    var buyerPhone: String? = null,
    var status: String? = null, // "pending", "accepted", "preparing", "ready", "delivered", "rejected"
    var totalAmount: Double? = null,
    var deliveryType: String? = null, // "pickup" or "delivery"
    var deliveryAddress: String? = null, // Full delivery address if deliveryType is "delivery"
    var deliveryPrice: Double? = null, // Delivery fee
    var timestamp: Long? = null,
    var items: MutableList<OrderItem>? = null
) : Serializable

data class OrderItem(
    var productId: String? = null,
    var productName: String? = null,
    var quantity: Int? = null,
    var price: Double? = null,
    var photoUrl: String? = null,
    var imageUrl: String? = null, // Alternative field name
    var maxStock: Int? = null,
    var sellerId: String? = null, // sellerId is inside each item!
    var totalPrice: Double? = null
) : Serializable

