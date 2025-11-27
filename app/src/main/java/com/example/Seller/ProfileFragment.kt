// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
// Import for Fragment class from AndroidX.
import androidx.fragment.app.Fragment
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
import com.google.firebase.database.*

/**
 * A Fragment that displays the seller's profile and allows them to edit it.
 * This class is very similar to ProfileActivity but is implemented as a fragment to be used within the HomeActivity's ViewPager.
 */
class ProfileFragment : Fragment() {

    // Firebase Authentication and Database references.
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    // UI elements.
    private lateinit var nameEt: EditText
    private lateinit var phoneEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var addressEt: EditText
    private lateinit var imageView: ImageView
    private lateinit var uploadBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var logoutBtn: Button
    private lateinit var voipCallBtn: Button
    private lateinit var progressBar: ProgressBar

    // The URI of the selected image.
    private var imageUri: Uri? = null
    // Request code for picking an image from the gallery.
    private val PICK_IMAGE_REQUEST = 100

    // AWS credentials and S3 bucket information.
    private val ACCESS_KEY = ""
    private val SECRET_KEY = ""
    private val BUCKET_NAME = ""
    private val REGION = Regions.EU_NORTH_1

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
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    /**
     * Called immediately after onCreateView() has returned, but before any saved state has been restored in to the view.
     * @param view The View returned by onCreateView().
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state as given here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onViewCreated(view, savedInstanceState)

        if (!isAdded) return

        // Initialize Firebase Auth and get the current user's ID.
        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        // Get a reference to the "Seller" node in the database.
        database = FirebaseDatabase.getInstance().getReference("Seller")

        // Initialize UI elements.
        nameEt = view.findViewById(R.id.etName)
        phoneEt = view.findViewById(R.id.etPhone)
        emailEt = view.findViewById(R.id.etEmail)
        addressEt = view.findViewById(R.id.etAddress)
        imageView = view.findViewById(R.id.profileImage)
        uploadBtn = view.findViewById(R.id.btnUpload)
        saveBtn = view.findViewById(R.id.btnSave)
        logoutBtn = view.findViewById(R.id.btnLogout)
        voipCallBtn = view.findViewById(R.id.btnVoIPCall)
        progressBar = view.findViewById(R.id.profileProgress)

        // If the user is not logged in, show a message and return.
        if (uid == null) {
            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Load the user's data.
        loadUserData(uid)

        // Set an OnClickListener for the upload button to open the image picker.
        uploadBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        // Set an OnClickListener for the save button to save the profile.
        saveBtn.setOnClickListener { saveProfile(uid) }

        // Set an OnClickListener for the VoIP call button to start the VoIPCallActivity.
        voipCallBtn.setOnClickListener {
            val intent = Intent(requireContext(), VoIPCallActivity::class.java)
            startActivity(intent)
        }

        // Set an OnClickListener for the logout button to sign the user out.
        logoutBtn.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()
        }
    }

    /**
     * Loads the user's data from the Firebase Realtime Database.
     * @param uid The ID of the current user.
     */
    private fun loadUserData(uid: String) {
        // Show the progress bar.
        progressBar.visibility = View.VISIBLE
        
        // Read the user's data from the database.
        database.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || view == null) return
                // Hide the progress bar.
                progressBar.visibility = View.GONE
                // Get the user profile from the snapshot.
                val user = snapshot.getValue(UserProfile::class.java)
                if (user != null) {
                    // If the user profile exists, populate the form fields with the data.
                    nameEt.setText(user.name)
                    phoneEt.setText(user.phone)
                    emailEt.setText(user.email ?: "")
                    emailEt.isEnabled = true
                    addressEt.setText(user.address)

                    // Load the profile image using Glide.
                    Glide.with(requireContext())
                        .load(user.photoUrl ?: R.drawable.ic_person)
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(imageView)
                } else {
                    // If the profile doesn't exist in the database, get the email from Firebase Auth.
                    val firebaseUser = auth.currentUser
                    val authEmail = firebaseUser?.email ?: ""
                    if (authEmail.isNotEmpty()) {
                        emailEt.setText(authEmail)
                        emailEt.isEnabled = true
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded && view != null) {
                    // Hide the progress bar.
                    progressBar.visibility = View.GONE
                    // If the database read fails, get the email from Firebase Auth.
                    val firebaseUser = auth.currentUser
                    val authEmail = firebaseUser?.email ?: ""
                    if (authEmail.isNotEmpty()) {
                        emailEt.setText(authEmail)
                        emailEt.isEnabled = true
                    }
                    Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    /**
     * Saves the user's profile to the Firebase Realtime Database.
     * @param uid The ID of the current user.
     */
    private fun saveProfile(uid: String) {
        // Get the new profile data from the form fields.
        val newName = nameEt.text.toString().trim()
        val newPhone = phoneEt.text.toString().trim()
        val newEmail = emailEt.text.toString().trim()
        val newAddress = addressEt.text.toString().trim()

        // Validate the input fields.
        if (newName.isEmpty()) {
            Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (newEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            Toast.makeText(requireContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }

        // Show the progress bar.
        progressBar.visibility = View.VISIBLE
        // Create a map of the updates to be saved.
        val updates = mutableMapOf<String, Any>(
            "name" to newName,
            "phone" to newPhone,
            "email" to newEmail,
            "address" to newAddress
        )

        // If a new image was selected, upload it to S3.
        if (imageUri != null) {
            Glide.with(requireContext())
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .into(imageView)

            uploadImageToS3(uid, imageUri!!) { imageUrl ->
                updates["photoUrl"] = imageUrl
                updateDatabase(uid, updates)
            }
        } else {
            // Otherwise, update the database directly.
            updateDatabase(uid, updates)
        }
    }

    /**
     * Uploads an image to an AWS S3 bucket.
     * @param uid The ID of the user, used in the image file name.
     * @param uri The URI of the image to be uploaded.
     * @param callback A function to be called with the URL of the uploaded image.
     */
    private fun uploadImageToS3(uid: String, uri: Uri, callback: (String) -> Unit) {
        // Create AWS credentials and an S3 client.
        val credentials = BasicAWSCredentials(ACCESS_KEY, SECRET_KEY)
        val s3Client = AmazonS3Client(credentials, com.amazonaws.regions.Region.getRegion(REGION))
        // Create a TransferUtility for handling the upload.
        val transferUtility = TransferUtility.builder()
            .context(requireContext())
            .s3Client(s3Client)
            .build()

        // Generate a unique file name for the image.
        val fileName = "seller_profile_images/$uid-${System.currentTimeMillis()}.jpg"
        // Create a temporary file from the image URI.
        val file = FileUtil.from(requireContext(), uri)

        // Start the upload.
        val uploadObserver = transferUtility.upload(
            BUCKET_NAME,
            fileName,
            file,
            CannedAccessControlList.PublicRead // Make the uploaded file publicly readable.
        )

        // Set a listener to track the progress of the upload.
        uploadObserver.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                if (state == TransferState.COMPLETED) {
                    // When the upload is complete, get the image URL and call the callback function.
                    val imageUrl = "https://$BUCKET_NAME.s3.amazonaws.com/$fileName"
                    callback(imageUrl)
                } else if (state == TransferState.FAILED) {
                    if (isAdded && view != null) {
                        // If the upload fails, hide the progress bar and show an error message.
                        progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Upload failed!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {}
            override fun onError(id: Int, ex: Exception?) {
                if (isAdded && view != null) {
                    // If an error occurs, hide the progress bar and show an error message.
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${ex?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    /**
     * Updates the user's profile data in the Firebase Realtime Database.
     * @param uid The ID of the user.
     * @param updates A map containing the data to be updated.
     */
    private fun updateDatabase(uid: String, updates: Map<String, Any>) {
        // Update the children of the user's node in the database.
        database.child(uid).updateChildren(updates).addOnCompleteListener {
            if (isAdded && view != null) {
                // Hide the progress bar.
                progressBar.visibility = View.GONE
                if (it.isSuccessful) {
                    // If the update is successful, show a success message.
                    Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show()
                } else {
                    // If the update fails, show an error message.
                    Toast.makeText(requireContext(), "Update failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            Glide.with(requireContext())
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .into(imageView)
        }
    }
}
