// The package name for the seller application.
package com.example.Seller

// Import for Serializable interface.
import java.io.Serializable

/**
 * A data class representing a product.
 * This class holds the information for a product, which is stored in the Firebase Realtime Database.
 * It implements Serializable to allow it to be passed between components.
 */
data class Product(
    // The unique ID of the product.
    var productId: String? = null,
    // The name of the product.
    var name: String? = null,
    // The price of the product.
    var price: Double? = null,
    // The current stock level of the product.
    var stock: Int? = null,
    // The URL of the product's photo.
    var photoUrl: String? = null
) : Serializable // Implementing Serializable allows this object to be passed in Intents.
