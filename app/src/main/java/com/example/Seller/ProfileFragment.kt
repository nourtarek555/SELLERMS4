package com.example.Seller

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

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

    private var imageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 100

    private val ACCESS_KEY = ""
    private val SECRET_KEY = ""
    private val BUCKET_NAME = ""
    private val REGION = Regions.EU_NORTH_1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!isAdded) return

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        database = FirebaseDatabase.getInstance().getReference("Seller")

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

        if (uid == null) {
            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
            return
        }

        loadUserData(uid)

        uploadBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        saveBtn.setOnClickListener { saveProfile(uid) }

        voipCallBtn.setOnClickListener {
            val intent = Intent(requireContext(), VoIPCallActivity::class.java)
            startActivity(intent)
        }

        logoutBtn.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()
        }
    }

    private fun loadUserData(uid: String) {
        progressBar.visibility = View.VISIBLE
        
        database.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || view == null) return
                progressBar.visibility = View.GONE
                val user = snapshot.getValue(UserProfile::class.java)
                if (user != null) {
                    nameEt.setText(user.name)
                    phoneEt.setText(user.phone)
                    // Use email from database (source of truth)
                    emailEt.setText(user.email ?: "")
                    emailEt.isEnabled = true
                    addressEt.setText(user.address)

                    Glide.with(requireContext())
                        .load(user.photoUrl ?: R.drawable.ic_person)
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(imageView)
                } else {
                    // If user profile doesn't exist in database, fallback to Firebase Auth email
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
                    progressBar.visibility = View.GONE
                    // Fallback to Firebase Auth email if database fails
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

    private fun saveProfile(uid: String) {
        val newName = nameEt.text.toString().trim()
        val newPhone = phoneEt.text.toString().trim()
        val newEmail = emailEt.text.toString().trim()
        val newAddress = addressEt.text.toString().trim()

        if (newName.isEmpty()) {
            Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (newEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            Toast.makeText(requireContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        val updates = mutableMapOf<String, Any>(
            "name" to newName,
            "phone" to newPhone,
            "email" to newEmail,
            "address" to newAddress
        )

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
            updateDatabase(uid, updates)
        }
    }

    private fun uploadImageToS3(uid: String, uri: Uri, callback: (String) -> Unit) {
        val credentials = BasicAWSCredentials(ACCESS_KEY, SECRET_KEY)
        val s3Client = AmazonS3Client(credentials, com.amazonaws.regions.Region.getRegion(REGION))
        val transferUtility = TransferUtility.builder()
            .context(requireContext())
            .s3Client(s3Client)
            .build()

        val fileName = "seller_profile_images/$uid-${System.currentTimeMillis()}.jpg"
        val file = FileUtil.from(requireContext(), uri)

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
                    if (isAdded && view != null) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Upload failed!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {}
            override fun onError(id: Int, ex: Exception?) {
                if (isAdded && view != null) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${ex?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun updateDatabase(uid: String, updates: Map<String, Any>) {
        database.child(uid).updateChildren(updates).addOnCompleteListener {
            if (isAdded && view != null) {
                progressBar.visibility = View.GONE
                if (it.isSuccessful) {
                    Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Update failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data
            Glide.with(requireContext())
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .into(imageView)
        }
    }
}

