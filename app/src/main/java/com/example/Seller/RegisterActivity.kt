package com.example.Seller

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val sellerRef = FirebaseDatabase.getInstance().getReference("Seller")

    private lateinit var nameEt: EditText
    private lateinit var phoneEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var addressEt: EditText
    private lateinit var passEt: EditText
    private lateinit var registerBtn: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        nameEt = findViewById(R.id.etName)
        phoneEt = findViewById(R.id.etPhone)
        emailEt = findViewById(R.id.etEmail)
        addressEt = findViewById(R.id.etAddress)
        passEt = findViewById(R.id.etPassword)
        registerBtn = findViewById(R.id.btnRegister)
        progressBar = findViewById(R.id.registerProgress)

        registerBtn.setOnClickListener {
            val name = nameEt.text.toString().trim()
            val phone = phoneEt.text.toString().trim()
            val email = emailEt.text.toString().trim()
            val address = addressEt.text.toString().trim()
            val password = passEt.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.length < 6) {
                Toast.makeText(this, "Enter valid name, email, and password (≥6 chars)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: return@addOnCompleteListener

                        val sellerProfile = UserProfile(
                            uid = uid,
                            name = name,
                            phone = phone,
                            email = email,
                            address = address,
                            appType = "Seller"
                        )

                        // Save seller data only to /Seller
                        sellerRef.child(uid).setValue(sellerProfile)
                            .addOnCompleteListener { dbTask ->
                                progressBar.visibility = View.GONE
                                if (dbTask.isSuccessful) {
                                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, ProfileActivity::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(this, "Database error: ${dbTask.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Auth error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }
}
