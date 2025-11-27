// The package name for the seller application.
package com.example.Seller

// Imports for Firebase Realtime Database.
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.Transaction
import com.google.firebase.database.DatabaseReference

/**
 * A utility object for validating order quantities against available inventory.
 * This object provides methods for both client-side and server-side validation,
 * as well as for updating inventory levels atomically.
 */
object InventoryValidator {

    /**
     * Validates if the ordered quantities are available in stock.
     * This is a client-side check that should be performed before placing an order.
     * It checks multiple items at once and returns a list of items with insufficient stock.
     * 
     * @param sellerId The ID of the seller whose products are being ordered.
     * @param orderItems A list of OrderItem objects representing the items in the order.
     * @param callback A function to be called with the validation result. It receives a boolean indicating if the order is valid
     *                 and a list of items with insufficient stock.
     */
    fun validateOrderQuantities(
        sellerId: String,
        orderItems: List<OrderItem>,
        callback: (isValid: Boolean, insufficientStockItems: List<InsufficientStockItem>) -> Unit
    ) {
        // If there are no items in the order, it's considered valid.
        if (orderItems.isEmpty()) {
            callback(true, emptyList())
            return
        }

        // Get an instance of Firebase Database.
        val database = FirebaseDatabase.getInstance()
        // Get a reference to the seller's products.
        val productsRef = database.getReference("Seller").child(sellerId).child("Products")
        
        // A list to store items that have insufficient stock.
        val validationResults = mutableListOf<InsufficientStockItem>()
        // A counter for the number of completed database checks.
        var completedChecks = 0
        // The total number of items to check.
        val totalItems = orderItems.size

        // If there are no items, the order is valid.
        if (totalItems == 0) {
            callback(true, emptyList())
            return
        }

        // Iterate over each item in the order.
        orderItems.forEach { item ->
            // Get the product ID and ordered quantity from the order item.
            val productId = item.productId ?: return@forEach // Skip if productId is null.
            val orderedQuantity = item.quantity ?: 0

            // If the ordered quantity is zero or less, we can skip the check for this item.
            if (orderedQuantity <= 0) {
                completedChecks++
                // If all checks are completed, invoke the callback.
                if (completedChecks == totalItems) {
                    callback(validationResults.isEmpty(), validationResults)
                }
                return@forEach
            }

            // Get a reference to the 'stock' of the specific product.
            productsRef.child(productId).child("stock")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    /**
                     * This method is called with a snapshot of the stock data.
                     * @param snapshot The data snapshot of the product's stock.
                     */
                    override fun onDataChange(snapshot: DataSnapshot) {
                        // Get the current stock value, handling different possible data types from Firebase.
                        val currentStock = when (val stockValue = snapshot.value) {
                            is Long -> stockValue.toInt()
                            is Int -> stockValue
                            is String -> stockValue.toIntOrNull() ?: 0
                            else -> 0
                        }

                        // If the ordered quantity is greater than the available stock, add it to the list of insufficient items.
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

                        // Increment the number of completed checks.
                        completedChecks++
                        // If all items have been checked, invoke the callback.
                        if (completedChecks == totalItems) {
                            callback(validationResults.isEmpty(), validationResults)
                        }
                    }

                    /**
                     * This method is called if the database read is cancelled.
                     * @param error The database error.
                     */
                    override fun onCancelled(error: DatabaseError) {
                        // On error, we consider the validation to have failed for this item.
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
                        // If all checks are complete, invoke the callback with a failure status.
                        if (completedChecks == totalItems) {
                            callback(false, validationResults)
                        }
                    }
                })
        }
    }

    /**
     * Validates a single product's quantity against available stock.
     * This is useful for real-time validation, for example, as a user is typing a quantity in a text field.
     * 
     * @param sellerId The ID of the seller.
     * @param productId The ID of the product to validate.
     * @param requestedQuantity The quantity being requested.
     * @param callback A function to be called with the validation result. It receives a boolean for validity,
     *                 the available quantity, and an optional error message.
     */
    fun validateProductQuantity(
        sellerId: String,
        productId: String,
        requestedQuantity: Int,
        callback: (isValid: Boolean, availableQuantity: Int, errorMessage: String?) -> Unit
    ) {
        // The requested quantity must be positive.
        if (requestedQuantity <= 0) {
            callback(false, 0, "Quantity must be greater than 0")
            return
        }

        // Get a reference to the product's stock in the database.
        val database = FirebaseDatabase.getInstance()
        val productStockRef = database.getReference("Seller")
            .child(sellerId)
            .child("Products")
            .child(productId)
            .child("stock")

        // Read the stock value once.
        productStockRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get the current stock, handling different data types.
                val currentStock = when (val stockValue = snapshot.value) {
                    is Long -> stockValue.toInt()
                    is Int -> stockValue
                    is String -> stockValue.toIntOrNull() ?: 0
                    else -> 0
                }

                // Check if the requested quantity is valid.
                val isValid = requestedQuantity <= currentStock
                // Prepare an error message if the stock is insufficient.
                val errorMessage = if (!isValid) {
                    "Insufficient stock. Available: $currentStock, Requested: $requestedQuantity"
                } else {
                    null
                }

                // Invoke the callback with the result.
                callback(isValid, currentStock, errorMessage)
            }

            override fun onCancelled(error: DatabaseError) {
                // If the database read fails, invoke the callback with an error.
                callback(false, 0, "Failed to check inventory: ${error.message}")
            }
        })
    }

    /**
     * Decrements the inventory for a list of order items using Firebase transactions for atomicity.
     * This ensures that stock is only decremented if all items in the order have sufficient stock.
     * This should be called when an order is placed to reserve the inventory.
     * 
     * @param sellerId The ID of the seller.
     * @param orderItems The list of items in the order.
     * @param callback A function to be called with the result of the operation. It receives a success boolean
     *                 and a list of items that failed the transaction (e.g., due to insufficient stock).
     */
    fun decrementInventoryForOrder(
        sellerId: String,
        orderItems: List<OrderItem>,
        callback: (success: Boolean, failedItems: List<InsufficientStockItem>) -> Unit
    ) {
        // If the order is empty, the operation is successful.
        if (orderItems.isEmpty()) {
            callback(true, emptyList())
            return
        }

        // Get a reference to the seller's products.
        val database = FirebaseDatabase.getInstance()
        val productsRef = database.getReference("Seller").child(sellerId).child("Products")

        // A list to store items that fail the transaction.
        val failedItems = mutableListOf<InsufficientStockItem>()
        var completedTransactions = 0
        val totalItems = orderItems.size
        var hasFailure = false

        // Iterate over each item in the order.
        orderItems.forEach { item ->
            val productId = item.productId ?: return@forEach
            val orderedQuantity = item.quantity ?: 0

            // Skip items with non-positive quantities.
            if (orderedQuantity <= 0) {
                completedTransactions++
                if (completedTransactions == totalItems) {
                    callback(!hasFailure, failedItems)
                }
                return@forEach
            }

            // Get a reference to the product's stock.
            val productStockRef = productsRef.child(productId).child("stock")

            // Use a transaction to atomically decrement the stock.
            productStockRef.runTransaction(object : Transaction.Handler {
                /**
                 * This method contains the logic for the transaction.
                 * It reads the current stock, and if sufficient, decrements it.
                 * @param mutableData The current data at the location.
                 * @return The result of the transaction.
                 */
                override fun doTransaction(mutableData: com.google.firebase.database.MutableData): Transaction.Result {
                    val currentValue = when (val value = mutableData.value) {
                        is Long -> value.toInt()
                        is Int -> value
                        is String -> value.toIntOrNull() ?: 0
                        else -> 0
                    }

                    // If there is enough stock, decrement it.
                    if (currentValue >= orderedQuantity) {
                        mutableData.value = currentValue - orderedQuantity
                        return Transaction.success(mutableData)
                    } else {
                        // If stock is insufficient, abort the transaction.
                        return Transaction.abort()
                    }
                }

                /**
                 * This method is called when the transaction is complete.
                 * @param error An error if the transaction failed, or null if it was successful.
                 * @param committed A boolean indicating whether the transaction was committed.
                 * @param currentData The data at the location after the transaction completed.
                 */
                override fun onComplete(
                    error: com.google.firebase.database.DatabaseError?,
                    committed: Boolean,
                    currentData: com.google.firebase.database.DataSnapshot?
                ) {
                    completedTransactions++

                    // If the transaction failed or was not committed...
                    if (error != null || !committed) {
                        hasFailure = true
                        val currentStock = when (val value = currentData?.value) {
                            is Long -> value.toInt()
                            is Int -> value
                            is String -> value.toIntOrNull() ?: 0
                            else -> 0
                        }
                        // Add the item to the list of failed items.
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

                    // When all transactions are complete, invoke the callback.
                    if (completedTransactions == totalItems) {
                        callback(!hasFailure, failedItems)
                    }
                }
            })
        }
    }

    /**
     * Restores the inventory for a list of order items (increments stock back).
     * This should be called when an order is rejected or cancelled by the seller.
     * 
     * @param sellerId The ID of the seller.
     * @param orderItems The list of items for which to restore inventory.
     * @param callback A function to be called with the result. It receives a success boolean and an optional error message.
     */
    fun restoreInventoryForOrder(
        sellerId: String,
        orderItems: List<OrderItem>,
        callback: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        // If there are no items, the operation is successful.
        if (orderItems.isEmpty()) {
            callback(true, null)
            return
        }

        // Get a reference to the seller's products.
        val database = FirebaseDatabase.getInstance()
        val productsRef = database.getReference("Seller").child(sellerId).child("Products")

        var completed = 0
        val total = orderItems.size
        var hasError = false
        var errorMessage: String? = null

        // Iterate over each item.
        orderItems.forEach { item ->
            val productId = item.productId ?: return@forEach
            val quantity = item.quantity ?: 0

            // Skip items with non-positive quantities.
            if (quantity <= 0) {
                completed++
                if (completed == total) {
                    callback(!hasError, errorMessage)
                }
                return@forEach
            }

            // Get a reference to the product's stock.
            productsRef.child(productId).child("stock")
                .get()
                .addOnSuccessListener { snapshot ->
                    // Get the current stock value.
                    val currentStock = when (val stockValue = snapshot.value) {
                        is Long -> stockValue.toInt()
                        is Int -> stockValue
                        is String -> stockValue.toIntOrNull() ?: 0
                        else -> 0
                    }

                    // Calculate the new stock.
                    val newStock = currentStock + quantity
                    // Set the new stock value in the database.
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
     * A data class to represent an item with insufficient stock during validation.
     * It holds information about the product, the requested quantity, and the available quantity.
     */
    data class InsufficientStockItem(
        val productId: String, // The ID of the product.
        val productName: String, // The name of the product.
        val requestedQuantity: Int, // The quantity that was requested.
        val availableQuantity: Int, // The quantity that is available in stock.
        val error: String? = null // An optional error message from the database.
    ) {
        /**
         * Returns a user-friendly error message for the item with insufficient stock.
         * @return A string containing the error message.
         */
        fun getErrorMessage(): String {
            return if (error != null) {
                "$productName: $error"
            } else {
                "$productName: Requested $requestedQuantity but only $availableQuantity available"
            }
        }
    }
}
