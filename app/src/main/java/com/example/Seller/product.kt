package com.example.Seller
import java.io.Serializable

data class Product(
    var productId: String? = null,
    var name: String? = null,
    var price: Double? = null,
    var stock: Int? = null,
    var photoUrl: String? = null
) : Serializable
