package com.example.Seller

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class ProductsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var nameEt: EditText
    private lateinit var priceEt: EditText
    private lateinit var stockEt: EditText
    private lateinit var imageView: ImageView
    private lateinit var uploadBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var progressBar: ProgressBar

    private var imageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 101

    // ⚠️ Replace these with your **real secure credentials in AWS config**, not hardcoded
    private val ACCESS_KEY = ""
    private val SECRET_KEY = ""
    private val BUCKET_NAME = ""
    private val REGION = Regions.EU_NORTH_1

    private var editingProduct: Product? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_items)

        auth = FirebaseAuth.getInstance()

        nameEt = findViewById(R.id.etName)
        priceEt = findViewById(R.id.price)
        stockEt = findViewById(R.id.stock)
        imageView = findViewById(R.id.profileImage)
        uploadBtn = findViewById(R.id.btnUpload)
        saveBtn = findViewById(R.id.btnSave)
        progressBar = findViewById(R.id.ProductProgress)

        // Check if we're editing an existing product
        editingProduct = intent.getSerializableExtra("product") as? Product
        if (editingProduct != null) {
            // Load product data for editing
            loadProductData(editingProduct!!)
            saveBtn.text = "Update Product"
        } else {
            saveBtn.text = "Add Product"
        }

        uploadBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        saveBtn.setOnClickListener {
            if (editingProduct != null) {
                updateProduct()
            } else {
                saveProduct()
            }
        }
    }

    private fun loadProductData(product: Product) {
        nameEt.setText(product.name ?: "")
        priceEt.setText(product.price?.toString() ?: "")
        stockEt.setText(product.stock?.toString() ?: "")
        
        if (product.photoUrl != null && product.photoUrl!!.isNotEmpty()) {
            Glide.with(this)
                .load(product.photoUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(imageView)
        }
    }

    private fun saveProduct() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        val name = nameEt.text.toString().trim()
        val priceText = priceEt.text.toString().trim()
        val stockText = stockEt.text.toString().trim()

        // ✅ validate inputs
        if (name.isEmpty() || priceText.isEmpty() || stockText.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val priceValue = priceText.toDoubleOrNull()
        val stockValue = stockText.toIntOrNull()

        if (priceValue == null || stockValue == null) {
            Toast.makeText(this, "Invalid price or stock value", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        val productId = UUID.randomUUID().toString()

        // ✅ store correct numeric types
        val productData = mutableMapOf<String, Any>(
            "productId" to productId,
            "name" to name,
            "price" to priceValue,
            "stock" to stockValue
        )

        if (imageUri != null) {
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .into(imageView)

            uploadImageToS3(productId, imageUri!!) { imageUrl ->
                productData["photoUrl"] = imageUrl
                updateDatabase(uid, productId, productData, isNew = true)
            }
        } else {
            updateDatabase(uid, productId, productData, isNew = true)
        }
    }

    private fun updateProduct() {
        val product = editingProduct ?: return
        val productId = product.productId ?: return
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        val name = nameEt.text.toString().trim()
        val priceText = priceEt.text.toString().trim()
        val stockText = stockEt.text.toString().trim()

        // ✅ validate inputs
        if (name.isEmpty() || priceText.isEmpty() || stockText.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val priceValue = priceText.toDoubleOrNull()
        val stockValue = stockText.toIntOrNull()

        if (priceValue == null || stockValue == null) {
            Toast.makeText(this, "Invalid price or stock value", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        // ✅ store correct numeric types
        val productData = mutableMapOf<String, Any>(
            "productId" to productId,
            "name" to name,
            "price" to priceValue,
            "stock" to stockValue
        )

        // Keep existing photoUrl if no new image is selected
        if (imageUri != null) {
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .into(imageView)

            uploadImageToS3(productId, imageUri!!) { imageUrl ->
                productData["photoUrl"] = imageUrl
                updateDatabase(uid, productId, productData, isNew = false)
            }
        } else {
            // Keep existing photoUrl if available
            if (product.photoUrl != null && product.photoUrl!!.isNotEmpty()) {
                productData["photoUrl"] = product.photoUrl!!
            }
            updateDatabase(uid, productId, productData, isNew = false)
        }
    }

    private fun uploadImageToS3(productId: String, uri: Uri, callback: (String) -> Unit) {
        val credentials = BasicAWSCredentials(ACCESS_KEY, SECRET_KEY)
        val s3Client = AmazonS3Client(credentials, com.amazonaws.regions.Region.getRegion(REGION))
        val transferUtility = TransferUtility.builder()
            .context(applicationContext)
            .s3Client(s3Client)
            .build()

        val fileName = "product_images/$productId-${System.currentTimeMillis()}.jpg"
        val file = FileUtil.from(this, uri)

        val uploadObserver = transferUtility.upload(
            BUCKET_NAME,
            fileName,
            file,
            CannedAccessControlList.PublicRead
        )

        uploadObserver.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                if (state == TransferState.COMPLETED) {
                    val imageUrl = "https://$BUCKET_NAME.s3.amazonaws.com/$fileName"
                    callback(imageUrl)
                } else if (state == TransferState.FAILED) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ProductsActivity, "Upload failed!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {}
            override fun onError(id: Int, ex: Exception?) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@ProductsActivity, "Error: ${ex?.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ✅ Save to: Seller/{uid}/Products/{productId}
    private fun updateDatabase(uid: String, productId: String, productData: Map<String, Any>, isNew: Boolean) {
        val databaseRef = FirebaseDatabase.getInstance()
            .getReference("Seller")
            .child(uid)
            .child("Products")
            .child(productId)

        databaseRef.setValue(productData).addOnCompleteListener {
            progressBar.visibility = View.GONE
            if (it.isSuccessful) {
                val message = if (isNew) "Product added successfully!" else "Product updated successfully!"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                clearFields()

                // Go back to previous activity
                finish()
            } else {
                val message = if (isNew) "Failed to add product" else "Failed to update product"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearFields() {
        nameEt.text.clear()
        priceEt.text.clear()
        stockEt.text.clear()
        imageView.setImageResource(R.drawable.ic_person)
        imageUri = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .into(imageView)
        }
    }
}
