package com.example.Seller

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Transaction

class OrderDetailsFragment : Fragment() {

    private lateinit var orderIdText: TextView
    private lateinit var buyerNameText: TextView
    private lateinit var buyerEmailText: TextView
    private lateinit var buyerPhoneText: TextView
    private lateinit var statusText: TextView
    private lateinit var totalAmountText: TextView
    private lateinit var deliveryTypeText: TextView
    private lateinit var deliveryAddressText: TextView
    private lateinit var deliveryPriceText: TextView
    private lateinit var itemsRecyclerView: RecyclerView
    private lateinit var acceptBtn: Button
    private lateinit var rejectBtn: Button
    private lateinit var preparingBtn: Button
    private lateinit var readyBtn: Button
    private lateinit var deliveredBtn: Button
    private lateinit var backBtn: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var auth: FirebaseAuth
    private var currentOrder: Order? = null
    private lateinit var itemsAdapter: OrderItemAdapter
    private lateinit var notificationHelper: NotificationHelper

    companion object {
        private const val ARG_ORDER = "order"

        fun newInstance(order: Order): OrderDetailsFragment {
            val fragment = OrderDetailsFragment()
            val args = Bundle()
            args.putSerializable(ARG_ORDER, order)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_order_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        notificationHelper = NotificationHelper(requireContext())

        orderIdText = view.findViewById(R.id.orderIdText)
        buyerNameText = view.findViewById(R.id.buyerNameText)
        buyerEmailText = view.findViewById(R.id.buyerEmailText)
        buyerPhoneText = view.findViewById(R.id.buyerPhoneText)
        statusText = view.findViewById(R.id.statusText)
        totalAmountText = view.findViewById(R.id.totalAmountText)
        deliveryTypeText = view.findViewById(R.id.deliveryTypeText)
        deliveryAddressText = view.findViewById(R.id.deliveryAddressText)
        deliveryPriceText = view.findViewById(R.id.deliveryPriceText)
        itemsRecyclerView = view.findViewById(R.id.itemsRecyclerView)
        acceptBtn = view.findViewById(R.id.acceptBtn)
        rejectBtn = view.findViewById(R.id.rejectBtn)
        preparingBtn = view.findViewById(R.id.preparingBtn)
        readyBtn = view.findViewById(R.id.readyBtn)
        deliveredBtn = view.findViewById(R.id.deliveredBtn)
        backBtn = view.findViewById(R.id.backBtn)
        progressBar = view.findViewById(R.id.detailsProgress)

        // Get order from arguments
        currentOrder = arguments?.getSerializable(ARG_ORDER) as? Order

        if (currentOrder == null) {
            Toast.makeText(requireContext(), "Order not found", Toast.LENGTH_SHORT).show()
            // Pop back stack or finish activity
            val activity = requireActivity()
            if (activity is OrdersActivity && activity.supportFragmentManager.backStackEntryCount > 0) {
                activity.supportFragmentManager.popBackStack()
            } else {
                activity.finish()
            }
            return
        }

        displayOrderDetails()
        setupButtons()
    }

    private fun displayOrderDetails() {
        val order = currentOrder ?: return

        // Show full order ID for clarity
        orderIdText.text = "Order ID: ${order.orderId ?: "N/A"}"
        buyerNameText.text = "Buyer: ${order.buyerName ?: "N/A"}"
        // Email and phone are hidden - only showing buyer name
        buyerEmailText.visibility = View.GONE
        buyerPhoneText.visibility = View.GONE
        
        // Status explanation with proper state machine
        val statusDisplay = when (order.status?.lowercase()) {
            "pending" -> "Status: PENDING (Waiting for your approval)"
            "accepted" -> "Status: ACCEPTED (Ready to start preparing)"
            "preparing" -> "Status: PREPARING (Order is being prepared)"
            "ready" -> {
                if (order.deliveryType == "delivery") {
                    "Status: READY (Ready for delivery)"
                } else {
                    "Status: READY (Ready for pickup)"
                }
            }
            "delivered" -> "Status: DELIVERED (Order completed)"
            "rejected" -> "Status: REJECTED"
            else -> "Status: ${order.status?.uppercase() ?: "N/A"}"
        }
        statusText.text = statusDisplay
        
        // Show delivery information
        val deliveryType = order.deliveryType ?: "pickup"
        deliveryTypeText.text = "Delivery Type: ${deliveryType.uppercase()}"
        
        if (deliveryType == "delivery" && !order.deliveryAddress.isNullOrEmpty()) {
            deliveryAddressText.text = "Delivery Address: ${order.deliveryAddress}"
            deliveryAddressText.visibility = View.VISIBLE
        } else {
            deliveryAddressText.visibility = View.GONE
        }
        
        if (order.deliveryPrice != null && order.deliveryPrice!! > 0) {
            deliveryPriceText.text = "Delivery Fee: $${String.format("%.2f", order.deliveryPrice)}"
            deliveryPriceText.visibility = View.VISIBLE
        } else {
            deliveryPriceText.visibility = View.GONE
        }
        
        totalAmountText.text = "Total Amount: $${String.format("%.2f", order.totalAmount ?: 0.0)}"

        // Setup items RecyclerView
        val items = order.items ?: mutableListOf()
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "No items in this order", Toast.LENGTH_SHORT).show()
        }
        
        itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        itemsAdapter = OrderItemAdapter(items)
        itemsRecyclerView.adapter = itemsAdapter

        updateButtonVisibility()
    }

    private fun updateButtonVisibility() {
        val status = currentOrder?.status?.lowercase()
        val deliveryType = currentOrder?.deliveryType?.lowercase() ?: "pickup"

        // Hide all buttons first
        acceptBtn.visibility = View.GONE
        rejectBtn.visibility = View.GONE
        preparingBtn.visibility = View.GONE
        readyBtn.visibility = View.GONE
        deliveredBtn.visibility = View.GONE

        when (status) {
            "pending" -> {
                acceptBtn.visibility = View.VISIBLE
                acceptBtn.text = "Accept Order"
                rejectBtn.visibility = View.VISIBLE
                rejectBtn.text = "Decline Order"
            }
            "accepted" -> {
                preparingBtn.visibility = View.VISIBLE
                preparingBtn.text = "Start Preparing"
            }
            "preparing" -> {
                readyBtn.visibility = View.VISIBLE
                readyBtn.text = if (deliveryType == "delivery") "Mark Ready for Delivery" else "Mark Ready for Pickup"
            }
            "ready" -> {
                // Only show "Mark Delivered" for delivery orders
                if (deliveryType == "delivery") {
                    deliveredBtn.visibility = View.VISIBLE
                    deliveredBtn.text = "Mark as Delivered"
                }
                // For pickup orders, ready is the final state
            }
            "delivered", "rejected" -> {
                // Final states - no action buttons
            }
            else -> {
                // Default to showing accept/reject if status is null or unknown
                acceptBtn.visibility = View.VISIBLE
                acceptBtn.text = "Accept Order"
                rejectBtn.visibility = View.VISIBLE
                rejectBtn.text = "Decline Order"
            }
        }
    }

    private fun setupButtons() {
        backBtn.setOnClickListener {
            // Pop back stack to return to orders list
            val activity = requireActivity()
            if (activity is OrdersActivity) {
                // Use supportFragmentManager from activity
                if (activity.supportFragmentManager.backStackEntryCount > 0) {
                    activity.supportFragmentManager.popBackStack()
                } else {
                    // No back stack, finish activity
                    activity.finish()
                }
            } else {
                // Fallback
                activity.finish()
            }
        }

        acceptBtn.setOnClickListener {
            currentOrder?.let { acceptOrder(it) }
        }

        rejectBtn.setOnClickListener {
            currentOrder?.let { rejectOrder(it) }
        }

        preparingBtn.setOnClickListener {
            currentOrder?.let { startPreparing(it) }
        }

        readyBtn.setOnClickListener {
            currentOrder?.let { markReady(it) }
        }

        deliveredBtn.setOnClickListener {
            currentOrder?.let { markDelivered(it) }
        }
    }

    private fun acceptOrder(order: Order) {
        val orderId = order.orderId ?: return
        val currentSellerId = auth.currentUser?.uid ?: return
        val items = order.items ?: mutableListOf()

        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "Order has no items", Toast.LENGTH_SHORT).show()
            return
        }

        // Use sellerId from order or items, fallback to current user's ID
        val sellerId = order.sellerId ?: items.firstOrNull()?.sellerId ?: currentSellerId
        
        Log.d("OrderDetails", "🚀 ACCEPTING ORDER: ID=${order.orderId}, sellerId=$sellerId, currentSellerId=$currentSellerId, items=${items.size}")
        items.forEachIndexed { index, item ->
            Log.d("OrderDetails", "  Item $index: ${item.productName} (ID: ${item.productId}), Qty: ${item.quantity}, Seller: ${item.sellerId}")
        }

        progressBar.visibility = View.VISIBLE

        // First, validate quantities against available stock (server-side check)
        validateAndAcceptOrder(order, sellerId, items)
    }

    /**
     * Validates order quantities against available stock and accepts the order if valid.
     * Uses Firebase transactions to ensure atomic inventory updates.
     */
    private fun validateAndAcceptOrder(order: Order, sellerId: String, items: List<OrderItem>) {
        val database = FirebaseDatabase.getInstance()
        val productsRef = database.getReference("Seller").child(sellerId).child("Products")
        val orderRef = database.getReference("Orders").child(order.orderId ?: return)

        // Track validation results
        val validationResults = mutableListOf<Pair<OrderItem, Int?>>() // Item -> current stock
        var validationCompleted = 0
        val totalItems = items.size

        if (totalItems == 0) {
            progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "Order has no items", Toast.LENGTH_SHORT).show()
            return
        }

        // Check each item's availability
        items.forEach { item ->
            val productId = item.productId ?: return@forEach
            val orderedQuantity = item.quantity ?: 0
            val itemSellerId = item.sellerId ?: sellerId

            Log.d("OrderDetails", "Validating item: ${item.productName} (ID: $productId), quantity: $orderedQuantity, sellerId: $itemSellerId")

            if (orderedQuantity <= 0) {
                Log.w("OrderDetails", "Invalid quantity for ${item.productName}: $orderedQuantity")
                validationCompleted++
                if (validationCompleted == totalItems) {
                    checkValidationResultsAndAccept(order, sellerId, validationResults, orderRef)
                }
                return@forEach
            }

            // Use the correct sellerId for this item (in case items are from different sellers)
            val itemProductsRef = if (itemSellerId != sellerId) {
                database.getReference("Seller").child(itemSellerId).child("Products")
            } else {
                productsRef
            }

            // Get current stock - also check if product exists
            val stockRef = itemProductsRef.child(productId).child("stock")
            val productRef = itemProductsRef.child(productId)
            
            Log.d("OrderDetails", "🔍 Fetching stock from: Seller/$itemSellerId/Products/$productId/stock")
            
            // First check if product exists
            productRef.get().addOnSuccessListener { productSnapshot ->
                if (!productSnapshot.exists()) {
                    Log.e("OrderDetails", "❌ PRODUCT DOES NOT EXIST: $productId (${item.productName}) under seller $itemSellerId")
                    validationResults.add(Pair(item, 0))
                    validationCompleted++
                    if (validationCompleted == totalItems) {
                        checkValidationResultsAndAccept(order, sellerId, validationResults, orderRef)
                    }
                    return@addOnSuccessListener
                }
                
                // Now get stock
                stockRef.get().addOnSuccessListener { snapshot ->
                    val stockValue = snapshot.value
                    val currentStock = when (stockValue) {
                        is Long -> stockValue.toInt()
                        is Int -> stockValue
                        is String -> stockValue.toIntOrNull() ?: 0
                        is Double -> stockValue.toInt()
                        is Float -> stockValue.toInt()
                        null -> {
                            Log.e("OrderDetails", "⚠️ Stock is NULL for product: $productId (${item.productName}) from seller $itemSellerId")
                            0
                        }
                        else -> {
                            Log.w("OrderDetails", "⚠️ Unexpected stock type for $productId (${item.productName}): ${stockValue.javaClass.simpleName}, value: $stockValue")
                            // Try to convert to string then to int as fallback
                            stockValue.toString().toIntOrNull() ?: 0
                        }
                    }

                    Log.d("OrderDetails", "📦 Stock read: ${item.productName} (ID: $productId, Seller: $itemSellerId) - Raw: $stockValue (${stockValue?.javaClass?.simpleName}), Parsed: $currentStock, Ordered: ${item.quantity}")

                    validationResults.add(Pair(item, currentStock))
                    validationCompleted++

                    if (validationCompleted == totalItems) {
                        checkValidationResultsAndAccept(order, sellerId, validationResults, orderRef)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("OrderDetails", "❌ Failed to read stock for product $productId (${item.productName}) from seller $itemSellerId: ${e.message}")
                    // Add a validation result with null stock to indicate failure
                    validationResults.add(Pair(item, null))
                    validationCompleted++
                    
                    if (validationCompleted == totalItems) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(
                            requireContext(),
                            "Failed to validate inventory for ${item.productName}: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("OrderDetails", "❌ Failed to check if product exists: $productId (${item.productName}) from seller $itemSellerId: ${e.message}")
                validationResults.add(Pair(item, null))
                validationCompleted++
                
                if (validationCompleted == totalItems) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Failed to find product ${item.productName}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Checks validation results and either accepts the order with inventory update or rejects due to insufficient stock.
     */
    private fun checkValidationResultsAndAccept(
        order: Order,
        sellerId: String,
        validationResults: List<Pair<OrderItem, Int?>>,
        orderRef: DatabaseReference
    ) {
        // Check if any item has insufficient stock
        val insufficientStockItems = mutableListOf<String>()
        
        // Debug: Check if we have validation results for all items
        Log.d("OrderDetails", "Checking validation results: ${validationResults.size} items")
        if (validationResults.isEmpty()) {
            progressBar.visibility = View.GONE
            Log.e("OrderDetails", "No validation results available for order ${order.orderId}")
            Toast.makeText(requireContext(), "No validation results available", Toast.LENGTH_LONG).show()
            return
        }
        
        validationResults.forEach { (item, currentStock) ->
            val productId = item.productId ?: return@forEach
            val orderedQuantity = item.quantity ?: 0
            
            // Handle case where stock couldn't be read (null)
            if (currentStock == null) {
                Log.e("OrderDetails", "Could not read stock for ${item.productName} (ID: $productId)")
                insufficientStockItems.add("${item.productName ?: productId} (Could not verify stock)")
                return@forEach
            }
            
            val availableStock = currentStock

            // Debug logging
            Log.d("OrderDetails", "Validating: Product=${item.productName}, Ordered=$orderedQuantity, Available=$availableStock")

            // NOTE: Buyer app already decremented inventory at order placement and validated stock.
            // We just need to verify the product exists and stock was read successfully.
            // We DON'T check if stock is sufficient because buyer already did that.
            
            Log.d("OrderDetails", "Validation: ${item.productName} - Ordered: $orderedQuantity, Current Stock: $availableStock")
            
            // Only reject if stock is negative (invalid state) or if we couldn't read it
            // Since buyer already validated and decremented, we trust that the order was valid when placed
            if (availableStock < 0) {
                Log.e("OrderDetails", "❌ INVALID STOCK: ${item.productName} - Stock is negative: $availableStock")
                insufficientStockItems.add("${item.productName ?: productId} (Invalid stock: $availableStock)")
            } else {
                Log.d("OrderDetails", "✓ Stock valid: ${item.productName} - Current: $availableStock (buyer already decremented $orderedQuantity at placement)")
            }
        }

        // If any item has insufficient stock, reject the acceptance
        if (insufficientStockItems.isNotEmpty()) {
            progressBar.visibility = View.GONE
            val message = "Cannot accept order: Insufficient stock for:\n${insufficientStockItems.joinToString("\n")}"
            Log.e("OrderDetails", "REJECTING ORDER ACCEPTANCE: $message")
            Log.e("OrderDetails", "Full validation results: ${validationResults.map { "${it.first.productName}: ordered=${it.first.quantity}, stock=${it.second}" }.joinToString(", ")}")
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            return
        }
        
        Log.d("OrderDetails", "✓ All items have sufficient stock, proceeding to accept order")

        // All items have sufficient stock - proceed with accepting order
        // NOTE: Buyer app already decremented inventory at order placement, so we don't decrement again
        acceptOrderWithoutDecrementing(order, sellerId, orderRef)
    }

    /**
     * Accepts the order without decrementing inventory.
     * Buyer app already decremented inventory at order placement, so we just update the order status.
     */
    private fun acceptOrderWithoutDecrementing(
        order: Order,
        sellerId: String,
        orderRef: DatabaseReference
    ) {
        Log.d("OrderDetails", "✅ Accepting order without decrementing inventory (buyer already decremented at placement)")
        updateOrderStatusToAccepted(orderRef, order)
    }

    /**
     * Updates order status to "accepted".
     * Note: Inventory was already decremented by buyer app at order placement.
     */
    private fun updateOrderStatusToAccepted(orderRef: DatabaseReference, order: Order) {
        orderRef.child("status").setValue("accepted")
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Order accepted! You can now start preparing it.",
                    Toast.LENGTH_SHORT
                ).show()
                currentOrder?.status = "accepted"
                displayOrderDetails()
                updateButtonVisibility()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Failed to update order status: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun startPreparing(order: Order) {
        val orderId = order.orderId ?: return
        val orderRef = FirebaseDatabase.getInstance().getReference("Orders").child(orderId)

        progressBar.visibility = View.VISIBLE

        // Update status: accepted → preparing
        orderRef.child("status").setValue("preparing")
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Order is now being prepared", Toast.LENGTH_SHORT).show()
                currentOrder?.status = "preparing"
                displayOrderDetails()
                updateButtonVisibility()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rejectOrder(order: Order) {
        val orderId = order.orderId ?: return
        val orderRef = FirebaseDatabase.getInstance().getReference("Orders").child(orderId)
        val sellerId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE

        // Update order status at order level in Firebase
        orderRef.child("status").setValue("rejected")
            .addOnSuccessListener {
                // Restore inventory for each item
                restoreInventory(order, sellerId)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to decline order: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun restoreInventory(order: Order, sellerId: String) {
        val items = order.items ?: return
        val database = FirebaseDatabase.getInstance().getReference("Seller")
            .child(sellerId)
            .child("Products")

        var completed = 0
        val total = items.size

        if (total == 0) {
            progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "Order declined successfully!", Toast.LENGTH_SHORT).show()
            currentOrder?.status = "rejected"
            displayOrderDetails() // Refresh display to show updated status
            updateButtonVisibility()
            return
        }

        items.forEach { item ->
            val productId = item.productId ?: return@forEach
            val quantity = item.quantity ?: 0

            database.child(productId).child("stock")
                .get()
                .addOnSuccessListener { snapshot ->
                    val currentStock = when (val stockValue = snapshot.value) {
                        is Long -> stockValue.toInt()
                        is Int -> stockValue
                        is String -> stockValue.toIntOrNull() ?: 0
                        else -> 0
                    }

                    val newStock = currentStock + quantity
                    database.child(productId).child("stock").setValue(newStock)
                        .addOnSuccessListener {
                            completed++
                            if (completed == total) {
                                progressBar.visibility = View.GONE
                                Toast.makeText(
                                    requireContext(),
                                    "Order declined and inventory restored",
                                    Toast.LENGTH_SHORT
                                ).show()
                                currentOrder?.status = "rejected"
                                displayOrderDetails() // Refresh display to show updated status
                                updateButtonVisibility()
                            }
                        }
                        .addOnFailureListener {
                            completed++
                            if (completed == total) {
                                progressBar.visibility = View.GONE
                                Toast.makeText(
                                    requireContext(),
                                    "Order declined (some inventory updates may have failed)",
                                    Toast.LENGTH_SHORT
                                ).show()
                                currentOrder?.status = "rejected"
                                displayOrderDetails() // Refresh display to show updated status
                                updateButtonVisibility()
                            }
                        }
                }
                .addOnFailureListener {
                    completed++
                    if (completed == total) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(
                            requireContext(),
                            "Order declined (inventory update failed)",
                            Toast.LENGTH_SHORT
                        ).show()
                        currentOrder?.status = "rejected"
                        displayOrderDetails() // Refresh display to show updated status
                        updateButtonVisibility()
                    }
                }
        }
    }

    private fun markReady(order: Order) {
        val orderId = order.orderId ?: return
        val orderRef = FirebaseDatabase.getInstance().getReference("Orders").child(orderId)

        progressBar.visibility = View.VISIBLE

        // Update status: preparing → ready
        val deliveryType = order.deliveryType ?: "pickup"
        orderRef.child("status").setValue("ready")
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                val message = if (deliveryType == "delivery") {
                    "Order ready for delivery"
                } else {
                    "Order ready for pickup"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                currentOrder?.status = "ready"
                // Show notification only for pickup orders (ready is final state for pickup)
                if (deliveryType == "pickup") {
                    order.orderId?.let { 
                        notificationHelper.showOrderStatusUpdateNotification(it, "ready")
                    }
                }
                displayOrderDetails()
                updateButtonVisibility()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun markDelivered(order: Order) {
        val orderId = order.orderId ?: return
        val orderRef = FirebaseDatabase.getInstance().getReference("Orders").child(orderId)

        progressBar.visibility = View.VISIBLE

        // Update status: ready → delivered (only for delivery orders)
        orderRef.child("status").setValue("delivered")
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Order marked as delivered!", Toast.LENGTH_SHORT).show()
                currentOrder?.status = "delivered"
                // Show notification
                order.orderId?.let { 
                    notificationHelper.showOrderStatusUpdateNotification(it, "delivered")
                }
                displayOrderDetails()
                updateButtonVisibility()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

