package com.example.Seller

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.Transaction
import com.google.firebase.database.DatabaseReference

/**
 * Utility class for validating order quantities against available inventory.
 * This provides both client-side and server-side validation capabilities.
 */
object InventoryValidator {

    /**
     * Validates if the ordered quantities are available in stock.
     * This is a client-side check that should be performed before placing an order.
     * 
     * @param sellerId The ID of the seller
     * @param orderItems List of order items to validate
     * @param callback Callback with validation result and list of items with insufficient stock
     */
    fun validateOrderQuantities(
        sellerId: String,
        orderItems: List<OrderItem>,
        callback: (isValid: Boolean, insufficientStockItems: List<InsufficientStockItem>) -> Unit
    ) {
        if (orderItems.isEmpty()) {
            callback(true, emptyList())
            return
        }

        val database = FirebaseDatabase.getInstance()
        val productsRef = database.getReference("Seller").child(sellerId).child("Products")
        
        val validationResults = mutableListOf<InsufficientStockItem>()
        var completedChecks = 0
        val totalItems = orderItems.size

        if (totalItems == 0) {
            callback(true, emptyList())
            return
        }

        orderItems.forEach { item ->
            val productId = item.productId ?: return@forEach
            val orderedQuantity = item.quantity ?: 0

            if (orderedQuantity <= 0) {
                completedChecks++
                if (completedChecks == totalItems) {
                    callback(validationResults.isEmpty(), validationResults)
                }
                return@forEach
            }

            productsRef.child(productId).child("stock")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val currentStock = when (val stockValue = snapshot.value) {
                            is Long -> stockValue.toInt()
                            is Int -> stockValue
                            is String -> stockValue.toIntOrNull() ?: 0
                            else -> 0
                        }

                        if (orderedQuantity > currentStock) {
                            validationResults.add(
                                InsufficientStockItem(
                                    productId = productId,
                                    productName = item.productName ?: "Unknown",
                                    requestedQuantity = orderedQuantity,
                                    availableQuantity = currentStock
                                )
                            )
                        }

                        completedChecks++
                        if (completedChecks == totalItems) {
                            callback(validationResults.isEmpty(), validationResults)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // On error, consider validation failed
                        validationResults.add(
                            InsufficientStockItem(
                                productId = productId,
                                productName = item.productName ?: "Unknown",
                                requestedQuantity = orderedQuantity,
                                availableQuantity = 0,
                                error = error.message
                            )
                        )
                        completedChecks++
                        if (completedChecks == totalItems) {
                            callback(false, validationResults)
                        }
                    }
                })
        }
    }

    /**
     * Validates a single product's quantity against available stock.
     * Useful for real-time validation as user changes quantities.
     * 
     * @param sellerId The ID of the seller
     * @param productId The product ID to validate
     * @param requestedQuantity The quantity being requested
     * @param callback Callback with validation result
     */
    fun validateProductQuantity(
        sellerId: String,
        productId: String,
        requestedQuantity: Int,
        callback: (isValid: Boolean, availableQuantity: Int, errorMessage: String?) -> Unit
    ) {
        if (requestedQuantity <= 0) {
            callback(false, 0, "Quantity must be greater than 0")
            return
        }

        val database = FirebaseDatabase.getInstance()
        val productStockRef = database.getReference("Seller")
            .child(sellerId)
            .child("Products")
            .child(productId)
            .child("stock")

        productStockRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentStock = when (val stockValue = snapshot.value) {
                    is Long -> stockValue.toInt()
                    is Int -> stockValue
                    is String -> stockValue.toIntOrNull() ?: 0
                    else -> 0
                }

                val isValid = requestedQuantity <= currentStock
                val errorMessage = if (!isValid) {
                    "Insufficient stock. Available: $currentStock, Requested: $requestedQuantity"
                } else {
                    null
                }

                callback(isValid, currentStock, errorMessage)
            }

            override fun onCancelled(error: DatabaseError) {
                callback(false, 0, "Failed to check inventory: ${error.message}")
            }
        })
    }

    /**
     * Decrements inventory for order items using Firebase transactions for atomicity.
     * This should be called when an order is placed to reserve inventory.
     * 
     * @param sellerId The ID of the seller
     * @param orderItems List of order items to decrement inventory for
     * @param callback Callback with success status and list of items that failed
     */
    fun decrementInventoryForOrder(
        sellerId: String,
        orderItems: List<OrderItem>,
        callback: (success: Boolean, failedItems: List<InsufficientStockItem>) -> Unit
    ) {
        if (orderItems.isEmpty()) {
            callback(true, emptyList())
            return
        }

        val database = FirebaseDatabase.getInstance()
        val productsRef = database.getReference("Seller").child(sellerId).child("Products")

        val failedItems = mutableListOf<InsufficientStockItem>()
        var completedTransactions = 0
        val totalItems = orderItems.size
        var hasFailure = false

        orderItems.forEach { item ->
            val productId = item.productId ?: return@forEach
            val orderedQuantity = item.quantity ?: 0

            if (orderedQuantity <= 0) {
                completedTransactions++
                if (completedTransactions == totalItems) {
                    callback(!hasFailure, failedItems)
                }
                return@forEach
            }

            val productStockRef = productsRef.child(productId).child("stock")

            // Use transaction to atomically decrement stock
            productStockRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: com.google.firebase.database.MutableData): Transaction.Result {
                    val currentValue = when (val value = mutableData.value) {
                        is Long -> value.toInt()
                        is Int -> value
                        is String -> value.toIntOrNull() ?: 0
                        else -> 0
                    }

                    // Check if stock is sufficient
                    if (currentValue >= orderedQuantity) {
                        mutableData.value = currentValue - orderedQuantity
                        return Transaction.success(mutableData)
                    } else {
                        // Stock is insufficient
                        return Transaction.abort()
                    }
                }

                override fun onComplete(
                    error: com.google.firebase.database.DatabaseError?,
                    committed: Boolean,
                    currentData: com.google.firebase.database.DataSnapshot?
                ) {
                    completedTransactions++

                    if (error != null || !committed) {
                        hasFailure = true
                        val currentStock = when (val value = currentData?.value) {
                            is Long -> value.toInt()
                            is Int -> value
                            is String -> value.toIntOrNull() ?: 0
                            else -> 0
                        }
                        failedItems.add(
                            InsufficientStockItem(
                                productId = productId,
                                productName = item.productName ?: "Unknown",
                                requestedQuantity = orderedQuantity,
                                availableQuantity = currentStock,
                                error = if (error != null) error.message else "Insufficient stock"
                            )
                        )
                    }

                    if (completedTransactions == totalItems) {
                        callback(!hasFailure, failedItems)
                    }
                }
            })
        }
    }

    /**
     * Restores inventory for order items (increments stock back).
     * This should be called when an order is rejected or cancelled.
     * 
     * @param sellerId The ID of the seller
     * @param orderItems List of order items to restore inventory for
     * @param callback Callback with success status
     */
    fun restoreInventoryForOrder(
        sellerId: String,
        orderItems: List<OrderItem>,
        callback: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        if (orderItems.isEmpty()) {
            callback(true, null)
            return
        }

        val database = FirebaseDatabase.getInstance()
        val productsRef = database.getReference("Seller").child(sellerId).child("Products")

        var completed = 0
        val total = orderItems.size
        var hasError = false
        var errorMessage: String? = null

        orderItems.forEach { item ->
            val productId = item.productId ?: return@forEach
            val quantity = item.quantity ?: 0

            if (quantity <= 0) {
                completed++
                if (completed == total) {
                    callback(!hasError, errorMessage)
                }
                return@forEach
            }

            productsRef.child(productId).child("stock")
                .get()
                .addOnSuccessListener { snapshot ->
                    val currentStock = when (val stockValue = snapshot.value) {
                        is Long -> stockValue.toInt()
                        is Int -> stockValue
                        is String -> stockValue.toIntOrNull() ?: 0
                        else -> 0
                    }

                    val newStock = currentStock + quantity
                    productsRef.child(productId).child("stock").setValue(newStock)
                        .addOnSuccessListener {
                            completed++
                            if (completed == total) {
                                callback(!hasError, errorMessage)
                            }
                        }
                        .addOnFailureListener { e ->
                            hasError = true
                            errorMessage = "Failed to restore inventory for ${item.productName}: ${e.message}"
                            completed++
                            if (completed == total) {
                                callback(false, errorMessage)
                            }
                        }
                }
                .addOnFailureListener { e ->
                    hasError = true
                    errorMessage = "Failed to get current stock for ${item.productName}: ${e.message}"
                    completed++
                    if (completed == total) {
                        callback(false, errorMessage)
                    }
                }
        }
    }

    /**
     * Data class representing an item with insufficient stock.
     */
    data class InsufficientStockItem(
        val productId: String,
        val productName: String,
        val requestedQuantity: Int,
        val availableQuantity: Int,
        val error: String? = null
    ) {
        fun getErrorMessage(): String {
            return if (error != null) {
                "$productName: $error"
            } else {
                "$productName: Requested $requestedQuantity but only $availableQuantity available"
            }
        }
    }
}

