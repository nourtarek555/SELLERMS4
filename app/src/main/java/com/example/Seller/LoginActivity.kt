package com.example.Seller

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEt: EditText
    private lateinit var passEt: EditText
    private lateinit var loginBtn: Button
    private lateinit var registerLink: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        emailEt = findViewById(R.id.etEmail)
        passEt = findViewById(R.id.etPassword)
        loginBtn = findViewById(R.id.btnLogin)
        registerLink = findViewById(R.id.tvRegister)
        progressBar = findViewById(R.id.loginProgress)

        // If already logged in, go directly to HomeActivity
        auth.currentUser?.let {
            goToHome()
        }

        loginBtn.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val password = passEt.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    progressBar.visibility = View.GONE
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null) {
                            checkSellerExists(user.uid)
                        }
                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun checkSellerExists(uid: String) {
        val dbRef = FirebaseDatabase.getInstance().getReference("Seller").child(uid)

        dbRef.get().addOnSuccessListener {
            if (it.exists()) {
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                goToHome()
            } else {
                Toast.makeText(this, "No seller account found for this user.", Toast.LENGTH_LONG).show()
                auth.signOut()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to verify account: ${it.message}", Toast.LENGTH_LONG).show()
            auth.signOut()
        }
    }

    private fun goToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
