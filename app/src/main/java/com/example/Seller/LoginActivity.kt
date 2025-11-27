// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
// Import for AppCompatActivity, a base class for activities that use the support library action bar features.
import androidx.appcompat.app.AppCompatActivity
// Imports for Firebase Authentication and Realtime Database.
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * An activity that handles user login.
 * This class allows users to sign in with their email and password.
 * It uses Firebase Authentication for user authentication and checks if the user is registered as a seller in the Firebase Realtime Database.
 */
class LoginActivity : AppCompatActivity() {

    // Firebase Authentication instance.
    private lateinit var auth: FirebaseAuth

    // UI elements.
    private lateinit var emailEt: EditText
    private lateinit var passEt: EditText
    private lateinit var loginBtn: Button
    private lateinit var registerLink: TextView
    private lateinit var progressBar: ProgressBar

    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onCreate(savedInstanceState)
        // Set the content view for the activity.
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth.
        auth = FirebaseAuth.getInstance()

        // Initialize UI elements from the layout.
        emailEt = findViewById(R.id.etEmail)
        passEt = findViewById(R.id.etPassword)
        loginBtn = findViewById(R.id.btnLogin)
        registerLink = findViewById(R.id.tvRegister)
        progressBar = findViewById(R.id.loginProgress)

        // If a user is already logged in, go directly to the HomeActivity.
        auth.currentUser?.let {
            goToHome()
        }

        // Set an OnClickListener for the login button.
        loginBtn.setOnClickListener {
            // Get the email and password from the EditTexts.
            val email = emailEt.text.toString().trim()
            val password = passEt.text.toString().trim()

            // Validate that the email and password are not empty.
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show the progress bar while logging in.
            progressBar.visibility = View.VISIBLE

            // Sign in the user with email and password using Firebase Auth.
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    // Hide the progress bar when the task is complete.
                    progressBar.visibility = View.GONE
                    // If the sign-in task is successful...
                    if (task.isSuccessful) {
                        // Get the current user.
                        val user = auth.currentUser
                        // If the user is not null, check if a seller record exists for this user.
                        if (user != null) {
                            checkSellerExists(user.uid)
                        }
                    } else {
                        // If login fails, show an error message.
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        // Set an OnClickListener for the register link.
        registerLink.setOnClickListener {
            // Start the RegisterActivity.
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    /**
     * Checks if a seller record exists in the Firebase Realtime Database for the given user ID.
     * This is to ensure that only registered sellers can log in to the seller app.
     * @param uid The unique ID of the user to check.
     */
    private fun checkSellerExists(uid: String) {
        // Get a reference to the "Seller" node in the database for the given UID.
        val dbRef = FirebaseDatabase.getInstance().getReference("Seller").child(uid)

        // Attempt to get the data at this location.
        dbRef.get().addOnSuccessListener {
            // If the data snapshot exists, it means the user is a registered seller.
            if (it.exists()) {
                // Show a success message.
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                // Proceed to the HomeActivity.
                goToHome()
            } else {
                // If no seller record is found, inform the user and sign them out.
                Toast.makeText(this, "No seller account found for this user.", Toast.LENGTH_LONG).show()
                auth.signOut()
            }
        }.addOnFailureListener {
            // If there's an error checking the database, show an error message and sign the user out.
            Toast.makeText(this, "Failed to verify account: ${it.message}", Toast.LENGTH_LONG).show()
            auth.signOut()
        }
    }

    /**
     * Navigates to the HomeActivity and finishes the current activity.
     * This function clears the activity stack to prevent the user from going back to the login screen.
     */
    private fun goToHome() {
        // Create an intent to start HomeActivity.
        val intent = Intent(this, HomeActivity::class.java)
        // Set flags to clear the activity stack and start a new task.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        // Start the activity.
        startActivity(intent)
        // Finish the current activity.
        finish()
    }
}
