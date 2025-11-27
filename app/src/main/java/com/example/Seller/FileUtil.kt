// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.content.Context
import android.net.Uri
// Imports for Java IO classes.
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * A utility object for file-related operations.
 * This object provides helper methods for working with files, such as creating a temporary file from a URI.
 */
object FileUtil {
    /**
     * Creates a temporary file from a given content URI.
     * This is useful for handling files selected from the device's storage,
     * as it provides a File object that can be used with APIs that require a file path.
     * @param context The application context.
     * @param uri The content URI of the file.
     * @return A File object representing the temporary file.
     */
    fun from(context: Context, uri: Uri): File {
        // Get an InputStream from the content resolver for the given URI.
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        // Create a temporary file in the application's cache directory.
        // The file name is generated using the current timestamp to ensure uniqueness.
        val file = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        // Create an FileOutputStream to write to the temporary file.
        val outputStream = FileOutputStream(file)
        // Copy the content from the InputStream to the FileOutputStream.
        inputStream?.copyTo(outputStream)
        // Close the output stream.
        outputStream.close()
        // Close the input stream.
        inputStream?.close()
        // Return the temporary file.
        return file
    }
}
