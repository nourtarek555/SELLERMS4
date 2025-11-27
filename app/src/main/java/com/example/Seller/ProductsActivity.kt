// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
// Import for AppCompatActivity.
import androidx.appcompat.app.AppCompatActivity
// Imports for AWS S3 client and transfer utility.
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
// Import for Glide, an image loading library.
import com.bumptech.glide.Glide
// Imports for Firebase Authentication and Realtime Database.
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
// Import for UUID for generating unique IDs.
import java.util.*

/**
 * An activity for adding and editing products.
 * This class provides a form for the seller to enter product details such as name, price, stock, and an image.
 * It handles uploading the product image to AWS S3 and saving the product data to Firebase Realtime Database.
 */
class ProductsActivity : AppCompatActivity() {

    // Firebase Authentication instance.
    private lateinit var auth: FirebaseAuth

    // UI elements.
    private lateinit var nameEt: EditText
    private lateinit var priceEt: EditText
    private lateinit var stockEt: EditText
    private lateinit var imageView: ImageView
    private lateinit var uploadBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var progressBar: ProgressBar

    // The URI of the selected image.
    private var imageUri: Uri? = null
    // Request code for picking an image from the gallery.
    private val PICK_IMAGE_REQUEST = 101

    // AWS credentials and S3 bucket information.
    // These should be stored securely and not hardcoded in a production environment.
    private val ACCESS_KEY = ""
    private val SECRET_KEY = ""
    private val BUCKET_NAME = ""
    private val REGION = Regions.EU_NORTH_1

    // The product being edited, if any.
    private var editingProduct: Product? = null

    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onCreate(savedInstanceState)
        // Set the content view for the activity.
        setContentView(R.layout.activity_items)

        // Initialize Firebase Auth.
        auth = FirebaseAuth.getInstance()

        // Initialize UI elements.
        nameEt = findViewById(R.id.etName)
        priceEt = findViewById(R.id.price)
        stockEt = findViewById(R.id.stock)
        imageView = findViewById(R.id.profileImage)
        uploadBtn = findViewById(R.id.btnUpload)
        saveBtn = findViewById(R.id.btnSave)
        progressBar = findViewById(R.id.ProductProgress)

        // Check if a product was passed in the intent for editing.
        editingProduct = intent.getSerializableExtra("product") as? Product
        if (editingProduct != null) {
            // If we are editing a product, load its data into the form.
            loadProductData(editingProduct!!)
            saveBtn.text = "Update Product"
        } else {
            // Otherwise, we are adding a new product.
            saveBtn.text = "Add Product"
        }

        // Set an OnClickListener for the upload button to open the image picker.
        uploadBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        // Set an OnClickListener for the save button.
        saveBtn.setOnClickListener {
            if (editingProduct != null) {
                // If we are editing, update the product.
                updateProduct()
            } else {
                // Otherwise, save a new product.
                saveProduct()
            }
        }
    }

    /**
     * Loads the data of an existing product into the form fields for editing.
     * @param product The product to be edited.
     */
    private fun loadProductData(product: Product) {
        nameEt.setText(product.name ?: "")
        priceEt.setText(product.price?.toString() ?: "")
        stockEt.setText(product.stock?.toString() ?: "")
        
        // Load the product image using Glide.
        if (product.photoUrl != null && product.photoUrl!!.isNotEmpty()) {
            Glide.with(this)
                .load(product.photoUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(imageView)
        }
    }

    /**
     * Saves a new product to the database.
     */
    private fun saveProduct() {
        // Get the current user's ID.
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        // Get the product details from the form.
        val name = nameEt.text.toString().trim()
        val priceText = priceEt.text.toString().trim()
        val stockText = stockEt.text.toString().trim()

        // Validate the input fields.
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

        // Show the progress bar.
        progressBar.visibility = View.VISIBLE
        // Generate a unique ID for the new product.
        val productId = UUID.randomUUID().toString()

        // Create a map of the product data.
        val productData = mutableMapOf<String, Any>(
            "productId" to productId,
            "name" to name,
            "price" to priceValue,
            "stock" to stockValue
        )

        // If an image was selected, upload it to S3.
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
            // If no image was selected, update the database directly.
            updateDatabase(uid, productId, productData, isNew = true)
        }
    }

    /**
     * Updates an existing product in the database.
     */
    private fun updateProduct() {
        val product = editingProduct ?: return
        val productId = product.productId ?: return
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        // Get the updated product details from the form.
        val name = nameEt.text.toString().trim()
        val priceText = priceEt.text.toString().trim()
        val stockText = stockEt.text.toString().trim()

        // Validate the input fields.
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

        // Show the progress bar.
        progressBar.visibility = View.VISIBLE

        // Create a map of the updated product data.
        val productData = mutableMapOf<String, Any>(
            "productId" to productId,
            "name" to name,
            "price" to priceValue,
            "stock" to stockValue
        )

        // If a new image was selected, upload it.
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
            // If no new image was selected, keep the existing one.
            if (product.photoUrl != null && product.photoUrl!!.isNotEmpty()) {
                productData["photoUrl"] = product.photoUrl!!
            }
            updateDatabase(uid, productId, productData, isNew = false)
        }
    }

    /**
     * Uploads an image to an AWS S3 bucket.
     * @param productId The ID of the product, used in the image file name.
     * @param uri The URI of the image to be uploaded.
     * @param callback A function to be called with the URL of the uploaded image.
     */
    private fun uploadImageToS3(productId: String, uri: Uri, callback: (String) -> Unit) {
        // Create AWS credentials and an S3 client.
        val credentials = BasicAWSCredentials(ACCESS_KEY, SECRET_KEY)
        val s3Client = AmazonS3Client(credentials, com.amazonaws.regions.Region.getRegion(REGION))
        // Create a TransferUtility for handling the upload.
        val transferUtility = TransferUtility.builder()
            .context(applicationContext)
            .s3Client(s3Client)
            .build()

        // Generate a unique file name for the image.
        val fileName = "product_images/$productId-${System.currentTimeMillis()}.jpg"
        // Create a temporary file from the image URI.
        val file = FileUtil.from(this, uri)

        // Start the upload.
        val uploadObserver = transferUtility.upload(
            BUCKET_NAME,
            fileName,
            file,
            CannedAccessControlList.PublicRead // Make the uploaded image publicly readable.
        )

        // Set a listener to track the progress of the upload.
        uploadObserver.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                if (state == TransferState.COMPLETED) {
                    // When the upload is complete, get the image URL and call the callback function.
                    val imageUrl = "https://$BUCKET_NAME.s3.amazonaws.com/$fileName"
                    callback(imageUrl)
                } else if (state == TransferState.FAILED) {
                    // If the upload fails, hide the progress bar and show an error message.
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ProductsActivity, "Upload failed!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {}
            override fun onError(id: Int, ex: Exception?) {
                // If an error occurs, hide the progress bar and show an error message.
                progressBar.visibility = View.GONE
                Toast.makeText(this@ProductsActivity, "Error: ${ex?.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Updates the product data in the Firebase Realtime Database.
     * @param uid The ID of the current user.
     * @param productId The ID of the product.
     * @param productData A map containing the product data.
     * @param isNew A boolean indicating whether this is a new product or an update.
     */
    private fun updateDatabase(uid: String, productId: String, productData: Map<String, Any>, isNew: Boolean) {
        // Get a reference to the product's location in the database.
        val databaseRef = FirebaseDatabase.getInstance()
            .getReference("Seller")
            .child(uid)
            .child("Products")
            .child(productId)

        // Set the value of the product data in the database.
        databaseRef.setValue(productData).addOnCompleteListener {
            // Hide the progress bar.
            progressBar.visibility = View.GONE
            if (it.isSuccessful) {
                // If the operation is successful, show a success message and clear the form.
                val message = if (isNew) "Product added successfully!" else "Product updated successfully!"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                clearFields()

                // Finish the activity to go back to the previous screen.
                finish()
            } else {
                // If the operation fails, show an error message.
                val message = if (isNew) "Failed to add product" else "Failed to update product"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Clears all the fields in the form.
     */
    private fun clearFields() {
        nameEt.text.clear()
        priceEt.text.clear()
        stockEt.text.clear()
        imageView.setImageResource(R.drawable.ic_person)
        imageUri = null
    }

    /**
     * Called when an activity you launched exits, giving you the requestCode you started it with, the resultCode it returned, and any additional data from it.
     * @param requestCode The integer request code originally supplied to startActivityForResult(), allowing you to identify who this result came from.
     * @param resultCode The integer result code returned by the child activity through its setResult().
     * @param data An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Call the superclass implementation.
        super.onActivityResult(requestCode, resultCode, data)
        // If the result is from the image picker and is successful...
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            // Get the URI of the selected image.
            imageUri = data.data
            // Load the selected image into the ImageView using Glide.
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .into(imageView)
        }
    }
}
