// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
// Import for AppCompatActivity.
import androidx.appcompat.app.AppCompatActivity
// Imports for Firebase Authentication and Realtime Database.
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * An activity for registering new sellers.
 * This class provides a form for users to create a new seller account.
 * It uses Firebase Authentication to create a new user and saves the seller's profile data to the Firebase Realtime Database.
 */
class RegisterActivity : AppCompatActivity() {

    // Firebase Authentication instance.
    private lateinit var auth: FirebaseAuth
    // Reference to the "Seller" node in the Firebase Realtime Database.
    private val sellerRef = FirebaseDatabase.getInstance().getReference("Seller")

    // UI elements.
    private lateinit var nameEt: EditText
    private lateinit var phoneEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var addressEt: EditText
    private lateinit var passEt: EditText
    private lateinit var registerBtn: Button
    private lateinit var progressBar: ProgressBar

    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onCreate(savedInstanceState)
        // Set the content view for the activity.
        setContentView(R.layout.activity_register)

        // Initialize Firebase Auth.
        auth = FirebaseAuth.getInstance()

        // Initialize UI elements.
        nameEt = findViewById(R.id.etName)
        phoneEt = findViewById(R.id.etPhone)
        emailEt = findViewById(R.id.etEmail)
        addressEt = findViewById(R.id.etAddress)
        passEt = findViewById(R.id.etPassword)
        registerBtn = findViewById(R.id.btnRegister)
        progressBar = findViewById(R.id.registerProgress)

        // Set an OnClickListener for the register button.
        registerBtn.setOnClickListener {
            // Get the user's input from the form.
            val name = nameEt.text.toString().trim()
            val phone = phoneEt.text.toString().trim()
            val email = emailEt.text.toString().trim()
            val address = addressEt.text.toString().trim()
            val password = passEt.text.toString().trim()

            // Validate the input.
            if (name.isEmpty() || email.isEmpty() || password.length < 6) {
                Toast.makeText(this, "Enter valid name, email, and password (≥6 chars)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Show the progress bar.
            progressBar.visibility = View.VISIBLE

            // Create a new user with email and password using Firebase Auth.
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // If the user is created successfully, get their UID.
                        val uid = auth.currentUser?.uid ?: return@addOnCompleteListener

                        // Create a UserProfile object with the seller's information.
                        val sellerProfile = UserProfile(
                            uid = uid,
                            name = name,
                            phone = phone,
                            email = email,
                            address = address,
                            appType = "Seller"
                        )

                        // Save the seller's profile to the "Seller" node in the database.
                        sellerRef.child(uid).setValue(sellerProfile)
                            .addOnCompleteListener { dbTask ->
                                // Hide the progress bar.
                                progressBar.visibility = View.GONE
                                if (dbTask.isSuccessful) {
                                    // If the profile is saved successfully, show a success message and go to the ProfileActivity.
                                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, ProfileActivity::class.java))
                                    finish()
                                } else {
                                    // If there's an error saving the profile, show an error message.
                                    Toast.makeText(this, "Database error: ${dbTask.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        // If there's an error creating the user, hide the progress bar and show an error message.
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Auth error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}
