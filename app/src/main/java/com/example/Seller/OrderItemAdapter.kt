package com.example.Seller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class OrderItemAdapter(private val itemList: List<OrderItem>) :
    RecyclerView.Adapter<OrderItemAdapter.OrderItemViewHolder>() {

    inner class OrderItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemImage: ImageView = itemView.findViewById(R.id.itemImage)
        val itemName: TextView = itemView.findViewById(R.id.itemName)
        val itemQuantity: TextView = itemView.findViewById(R.id.itemQuantity)
        val itemPrice: TextView = itemView.findViewById(R.id.itemPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.order_item_row, parent, false)
        return OrderItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderItemViewHolder, position: Int) {
        val item = itemList[position]

        holder.itemName.text = item.productName ?: "Unknown Product"
        holder.itemQuantity.text = "Quantity: ${item.quantity ?: 0}"
        
        // Use totalPrice if available, otherwise calculate from price * quantity
        val itemTotal = item.totalPrice ?: ((item.price ?: 0.0) * (item.quantity ?: 0))
        holder.itemPrice.text = "Total: $${String.format("%.2f", itemTotal)}"

        // Use imageUrl if photoUrl is null (database uses imageUrl)
        val imageUrl = item.photoUrl ?: item.imageUrl
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .placeholder(R.drawable.ic_person)
            .error(R.drawable.ic_person)
            .into(holder.itemImage)
    }

    override fun getItemCount(): Int = itemList.size
}

