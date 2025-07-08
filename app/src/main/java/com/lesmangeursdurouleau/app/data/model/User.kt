// PRÊT À COLLER - Fichier User.kt
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    var uid: String = "",
    val username: String = "",
    val email: String = "",
    val profilePictureUrl: String? = null,

    @ServerTimestamp
    val createdAt: Date? = null,

    val bio: String? = null,
    val city: String? = null,
    val canEditReadings: Boolean = false,
    val lastPermissionGrantedTimestamp: Long? = null,
    var followersCount: Int = 0,
    var followingCount: Int = 0,
    var booksReadCount: Int = 0,

    @get:PropertyName("isOnline")
    val isOnline: Boolean = false,

    @ServerTimestamp
    val lastSeen: Date? = null,

    val highestAffinityScore: Int = 0,
    val highestAffinityPartnerId: String? = null,
    val highestAffinityPartnerUsername: String? = null,
    val highestAffinityTierName: String? = null,

    // CORRECTION : Champ ajouté pour correspondre à Firestore
    val isEmailVerified: Boolean = false,

    val fcmToken: String? = null,
    val isTypingInGeneralChat: Boolean = false
)