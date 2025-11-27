// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.os.Bundle
// Import for enabling edge-to-edge display.
import androidx.activity.enableEdgeToEdge
// Import for AppCompatActivity.
import androidx.appcompat.app.AppCompatActivity
// Imports for handling window insets and view compatibility.
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * The main entry point of the application.
 * This activity is launched when the user starts the application.
 * It sets up the main layout and handles system UI adjustments.
 */
class MainActivity : AppCompatActivity() {
    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge display, allowing the app to draw under the system bars.
        enableEdgeToEdge()
        // Set the content view for the activity.
        setContentView(R.layout.activity_main)
        // Set an OnApplyWindowInsetsListener to the main view to handle system bars padding.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            // Get the insets for the system bars (status bar, navigation bar).
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the insets as padding to the view.
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            // Return the insets to be consumed by the system.
            insets
        }
    }
}
