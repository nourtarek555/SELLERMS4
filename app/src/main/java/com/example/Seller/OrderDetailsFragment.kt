// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
// Import for Fragment class from AndroidX.
import androidx.fragment.app.Fragment
// Imports for RecyclerView and its layout manager.
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// Imports for Firebase Authentication and Realtime Database.
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Transaction

/**
 * A Fragment that displays the details of a specific order.
 * This class shows all the information about an order and allows the seller to manage the order status (accept, reject, etc.).
 * It receives an Order object as an argument.
 */
class OrderDetailsFragment : Fragment() {

    // UI elements for displaying order details.
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

    // Buttons for order management.
    private lateinit var acceptBtn: Button
    private lateinit var rejectBtn: Button
    private lateinit var preparingBtn: Button
    private lateinit var readyBtn: Button
    private lateinit var deliveredBtn: Button
    private lateinit var backBtn: Button

    // Progress bar for showing loading status.
    private lateinit var progressBar: ProgressBar

    // Firebase Authentication instance.
    private lateinit var auth: FirebaseAuth
    // The current order being displayed.
    private var currentOrder: Order? = null
    // Adapter for the RecyclerView that displays the order items.
    private lateinit var itemsAdapter: OrderItemAdapter
    // Helper class for showing notifications.
    private lateinit var notificationHelper: NotificationHelper

    // Companion object to provide a factory method for creating instances of this fragment.
    companion object {
        // The key for the order argument in the fragment's arguments bundle.
        private const val ARG_ORDER = "order"

        /**
         * Creates a new instance of OrderDetailsFragment with the given order.
         * @param order The Order object to be displayed.
         * @return A new instance of OrderDetailsFragment.
         */
        fun newInstance(order: Order): OrderDetailsFragment {
            val fragment = OrderDetailsFragment()
            val args = Bundle()
            args.putSerializable(ARG_ORDER, order)
            fragment.arguments = args
            return fragment
        }
    }

    /**
     * Called to have the fragment instantiate its user interface view.
     * @param inflater The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container If non-null, this is the parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state as given here.
     * @return Return the View for the fragment's UI, or null.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment.
        return inflater.inflate(R.layout.fragment_order_details, container, false)
    }

    /**
     * Called immediately after onCreateView() has returned, but before any saved state has been restored in to the view.
     * @param view The View returned by onCreateView().
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state as given here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase Auth and NotificationHelper.
        auth = FirebaseAuth.getInstance()
        notificationHelper = NotificationHelper(requireContext())

        // Initialize all UI elements by finding them in the view.
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

        // Get the order from the fragment's arguments.
        currentOrder = arguments?.getSerializable(ARG_ORDER) as? Order

        // If the order is not found, show an error and close the fragment.
        if (currentOrder == null) {
            Toast.makeText(requireContext(), "Order not found", Toast.LENGTH_SHORT).show()
            val activity = requireActivity()
            if (activity is OrdersActivity && activity.supportFragmentManager.backStackEntryCount > 0) {
                activity.supportFragmentManager.popBackStack()
            } else {
                activity.finish()
            }
            return
        }

        // Display the order details and set up the buttons.
        displayOrderDetails()
        setupButtons()
    }

    /**
     * Displays the details of the current order in the UI.
     */
    private fun displayOrderDetails() {
        // Get the current order, or return if it's null.
        val order = currentOrder ?: return

        // Set the text for the various TextViews with the order data.
        orderIdText.text = "Order ID: ${order.orderId ?: "N/A"}"
        buyerNameText.text = "Buyer: ${order.buyerName ?: "N/A"}"
        // Buyer's email and phone are hidden for privacy.
        buyerEmailText.visibility = View.GONE
        buyerPhoneText.visibility = View.GONE
        
        // Display the order status with a more descriptive text.
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
        
        // Display delivery information.
        val deliveryType = order.deliveryType ?: "pickup"
        deliveryTypeText.text = "Delivery Type: ${deliveryType.uppercase()}"
        
        // Show the delivery address only if it's a delivery order and the address is available.
        if (deliveryType == "delivery" && !order.deliveryAddress.isNullOrEmpty()) {
            deliveryAddressText.text = "Delivery Address: ${order.deliveryAddress}"
            deliveryAddressText.visibility = View.VISIBLE
        } else {
            deliveryAddressText.visibility = View.GONE
        }
        
        // Show the delivery price only if it's greater than zero.
        if (order.deliveryPrice != null && order.deliveryPrice!! > 0) {
            deliveryPriceText.text = "Delivery Fee: $${String.format("%.2f", order.deliveryPrice)}"
            deliveryPriceText.visibility = View.VISIBLE
        } else {
            deliveryPriceText.visibility = View.GONE
        }
        
        // Display the total amount of the order.
        totalAmountText.text = "Total Amount: $${String.format("%.2f", order.totalAmount ?: 0.0)}"

        // Set up the RecyclerView for displaying the order items.
        val items = order.items ?: mutableListOf()
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "No items in this order", Toast.LENGTH_SHORT).show()
        }
        
        itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        itemsAdapter = OrderItemAdapter(items)
        itemsRecyclerView.adapter = itemsAdapter

        // Update the visibility of the action buttons based on the order status.
        updateButtonVisibility()
    }

    /**
     * Updates the visibility of the order action buttons based on the current order status.
     */
    private fun updateButtonVisibility() {
        val status = currentOrder?.status?.lowercase()
        val deliveryType = currentOrder?.deliveryType?.lowercase() ?: "pickup"

        // Hide all action buttons by default.
        acceptBtn.visibility = View.GONE
        rejectBtn.visibility = View.GONE
        preparingBtn.visibility = View.GONE
        readyBtn.visibility = View.GONE
        deliveredBtn.visibility = View.GONE

        // Show the appropriate buttons based on the order status.
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
                // The "Mark Delivered" button is only shown for delivery orders.
                if (deliveryType == "delivery") {
                    deliveredBtn.visibility = View.VISIBLE
                    deliveredBtn.text = "Mark as Delivered"
                }
            }
            "delivered", "rejected" -> {
                // These are final states, so no action buttons are shown.
            }
            else -> {
                // If the status is unknown, default to showing the accept/reject buttons.
                acceptBtn.visibility = View.VISIBLE
                acceptBtn.text = "Accept Order"
                rejectBtn.visibility = View.VISIBLE
                rejectBtn.text = "Decline Order"
            }
        }
    }

    /**
     * Sets up the OnClickListeners for all the buttons in the fragment.
     */
    private fun setupButtons() {
        // Set up the back button to navigate back to the orders list.
        backBtn.setOnClickListener {
            val activity = requireActivity()
            if (activity is OrdersActivity) {
                if (activity.supportFragmentManager.backStackEntryCount > 0) {
                    activity.supportFragmentManager.popBackStack()
                } else {
                    activity.finish()
                }
            } else {
                activity.finish()
            }
        }

        // Set up the listeners for the order action buttons.
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

    /**
     * Accepts the order.
     * This function initiates the process of validating the order and updating its status.
     * @param order The order to be accepted.
     */
    private fun acceptOrder(order: Order) {
        val orderId = order.orderId ?: return
        val currentSellerId = auth.currentUser?.uid ?: return
        val items = order.items ?: mutableListOf()

        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "Order has no items", Toast.LENGTH_SHORT).show()
            return
        }

        // Determine the seller ID for the order.
        val sellerId = order.sellerId ?: items.firstOrNull()?.sellerId ?: currentSellerId
        
        // Log the details of the order being accepted for debugging.
        Log.d("OrderDetails", "🚀 ACCEPTING ORDER: ID=${order.orderId}, sellerId=$sellerId, currentSellerId=$currentSellerId, items=${items.size}")
        items.forEachIndexed { index, item ->
            Log.d("OrderDetails", "  Item $index: ${item.productName} (ID: ${item.productId}), Qty: ${item.quantity}, Seller: ${item.sellerId}")
        }

        // Show the progress bar.
        progressBar.visibility = View.VISIBLE

        // Validate the order quantities against the available stock.
        validateAndAcceptOrder(order, sellerId, items)
    }

    /**
     * Validates the order quantities against available stock and accepts the order if valid.
     * This function uses Firebase transactions to ensure atomic inventory updates.
     * @param order The order to be validated and accepted.
     * @param sellerId The ID of the seller.
     * @param items The list of items in the order.
     */
    private fun validateAndAcceptOrder(order: Order, sellerId: String, items: List<OrderItem>) {
        val database = FirebaseDatabase.getInstance()
        val productsRef = database.getReference("Seller").child(sellerId).child("Products")
        val orderRef = database.getReference("Orders").child(order.orderId ?: return)

        val validationResults = mutableListOf<Pair<OrderItem, Int?>>() // Stores pairs of order items and their current stock.
        var validationCompleted = 0
        val totalItems = items.size

        if (totalItems == 0) {
            progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "Order has no items", Toast.LENGTH_SHORT).show()
            return
        }

        // Check the availability of each item in the order.
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

            // Use the correct seller ID for the item, in case items are from different sellers.
            val itemProductsRef = if (itemSellerId != sellerId) {
                database.getReference("Seller").child(itemSellerId).child("Products")
            } else {
                productsRef
            }

            // Get a reference to the product's stock.
            val stockRef = itemProductsRef.child(productId).child("stock")
            val productRef = itemProductsRef.child(productId)
            
            Log.d("OrderDetails", "🔍 Fetching stock from: Seller/$itemSellerId/Products/$productId/stock")
            
            // First, check if the product exists.
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
                
                // Now, get the stock value.
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
     * Checks the validation results and either accepts the order with an inventory update or rejects it due to insufficient stock.
     * @param order The order being processed.
     * @param sellerId The ID of the seller.
     * @param validationResults A list of pairs, each containing an order item and its current stock.
     * @param orderRef A reference to the order in the Firebase database.
     */
    private fun checkValidationResultsAndAccept(
        order: Order,
        sellerId: String,
        validationResults: List<Pair<OrderItem, Int?>>,
        orderRef: DatabaseReference
    ) {
        val insufficientStockItems = mutableListOf<String>()
        
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
            
            if (currentStock == null) {
                Log.e("OrderDetails", "Could not read stock for ${item.productName} (ID: $productId)")
                insufficientStockItems.add("${item.productName ?: productId} (Could not verify stock)")
                return@forEach
            }
            
            val availableStock = currentStock

            Log.d("OrderDetails", "Validating: Product=${item.productName}, Ordered=$orderedQuantity, Available=$availableStock")

            // Since the buyer app already decremented the inventory when the order was placed,
            // we just need to verify that the product exists and the stock was read successfully.
            Log.d("OrderDetails", "Validation: ${item.productName} - Ordered: $orderedQuantity, Current Stock: $availableStock")
            
            // We only reject if the stock is negative (which indicates an invalid state) or if we couldn't read it.
            if (availableStock < 0) {
                Log.e("OrderDetails", "❌ INVALID STOCK: ${item.productName} - Stock is negative: $availableStock")
                insufficientStockItems.add("${item.productName ?: productId} (Invalid stock: $availableStock)")
            } else {
                Log.d("OrderDetails", "✓ Stock valid: ${item.productName} - Current: $availableStock (buyer already decremented $orderedQuantity at placement)")
            }
        }

        // If any item has insufficient stock, we cannot accept the order.
        if (insufficientStockItems.isNotEmpty()) {
            progressBar.visibility = View.GONE
            val message = "Cannot accept order: Insufficient stock for:\n${insufficientStockItems.joinToString("\n")}"
            Log.e("OrderDetails", "REJECTING ORDER ACCEPTANCE: $message")
            Log.e("OrderDetails", "Full validation results: ${validationResults.map { "${it.first.productName}: ordered=${it.first.quantity}, stock=${it.second}" }.joinToString(", ")}")
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            return
        }
        
        Log.d("OrderDetails", "✓ All items have sufficient stock, proceeding to accept order")

        // If all items have sufficient stock, we can accept the order.
        // The inventory has already been decremented by the buyer app, so we don't need to do it again here.
        acceptOrderWithoutDecrementing(order, sellerId, orderRef)
    }

    /**
     * Accepts the order without decrementing the inventory.
     * This is called because the buyer app has already handled the inventory decrement.
     * We just need to update the order status.
     * @param order The order to be accepted.
     * @param sellerId The ID of the seller.
     * @param orderRef A reference to the order in the Firebase database.
     */
    private fun acceptOrderWithoutDecrementing(
        order: Order,
        sellerId: String,
        orderRef: DatabaseReference
    ) {
        Log.d("OrderDetails", "✅ Accepting order without decrementing inventory (buyer already decremented at placement)")
        // Update the order status to "accepted".
        updateOrderStatusToAccepted(orderRef, order)
    }

    /**
     * Updates the order status to "accepted" in the Firebase database.
     * @param orderRef A reference to the order in the Firebase database.
     * @param order The order being updated.
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
                // Update the local order status and refresh the UI.
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

    /**
     * Updates the order status to "preparing".
     * @param order The order to be updated.
     */
    private fun startPreparing(order: Order) {
        val orderId = order.orderId ?: return
        val orderRef = FirebaseDatabase.getInstance().getReference("Orders").child(orderId)

        progressBar.visibility = View.VISIBLE

        // Set the status of the order to "preparing".
        orderRef.child("status").setValue("preparing")
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Order is now being prepared", Toast.LENGTH_SHORT).show()
                // Update the local order status and refresh the UI.
                currentOrder?.status = "preparing"
                displayOrderDetails()
                updateButtonVisibility()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Rejects the order.
     * This function updates the order status to "rejected" and restores the inventory for the items in the order.
     * @param order The order to be rejected.
     */
    private fun rejectOrder(order: Order) {
        val orderId = order.orderId ?: return
        val orderRef = FirebaseDatabase.getInstance().getReference("Orders").child(orderId)
        val sellerId = auth.currentUser?.uid ?: return

        progressBar.visibility = View.VISIBLE

        // Set the status of the order to "rejected".
        orderRef.child("status").setValue("rejected")
            .addOnSuccessListener {
                // If the status is updated successfully, restore the inventory.
                restoreInventory(order, sellerId)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to decline order: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Restores the inventory for the items in a rejected order.
     * This function increments the stock count for each item in the order.
     * @param order The order for which to restore the inventory.
     * @param sellerId The ID of the seller.
     */
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
            displayOrderDetails()
            updateButtonVisibility()
            return
        }

        // Iterate over each item and restore the stock.
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
                                displayOrderDetails()
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
                                displayOrderDetails()
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
                        displayOrderDetails()
                        updateButtonVisibility()
                    }
                }
        }
    }

    /**
     * Marks the order as ready for pickup or delivery.
     * @param order The order to be marked as ready.
     */
    private fun markReady(order: Order) {
        val orderId = order.orderId ?: return
        val orderRef = FirebaseDatabase.getInstance().getReference("Orders").child(orderId)

        progressBar.visibility = View.VISIBLE

        val deliveryType = order.deliveryType ?: "pickup"
        // Set the status of the order to "ready".
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
                // Send a notification to the user if the order is ready for pickup.
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

    /**
     * Marks the order as delivered.
     * This is only applicable for delivery orders.
     * @param order The order to be marked as delivered.
     */
    private fun markDelivered(order: Order) {
        val orderId = order.orderId ?: return
        val orderRef = FirebaseDatabase.getInstance().getReference("Orders").child(orderId)

        progressBar.visibility = View.VISIBLE

        // Set the status of the order to "delivered".
        orderRef.child("status").setValue("delivered")
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Order marked as delivered!", Toast.LENGTH_SHORT).show()
                currentOrder?.status = "delivered"
                // Send a notification to the user that the order has been delivered.
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
