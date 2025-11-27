// The package name for the seller application.
package com.example.Seller

/**
 * A data class representing a user's profile.
 * This class holds the information for a seller's profile, which is stored in the Firebase Realtime Database.
 */
data class UserProfile(
    // The unique ID of the user.
    var uid: String = "",
    // The name of the user.
    var name: String = "",
    // The phone number of the user.
    var phone: String = "",
    // The email address of the user.
    var email: String = "",
    // The address of the user.
    var address: String = "",
    // The type of application the user is registered for (e.g., "Seller").
    var appType: String = "",
    // The URL of the user's profile photo.
    var photoUrl: String = ""
)
