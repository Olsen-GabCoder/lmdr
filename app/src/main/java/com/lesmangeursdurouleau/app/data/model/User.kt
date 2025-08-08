// PRÊT À COLLER - Remplacez tout le contenu de votre fichier User.kt
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class UserListItem(
    val uid: String = "",
    val username: String = "",
    val profilePictureUrl: String? = null
)

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val profilePictureUrl: String? = null,
    val coverPictureUrl: String? = null,

    @ServerTimestamp
    val createdAt: Date? = null,

    val bio: String? = null,
    val city: String? = null,

    // DÉPRÉCIÉ: Ce champ est maintenant géré par le système de rôles.
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

    val isEmailVerified: Boolean = false,
    val fcmToken: String? = null,
    val isTypingInGeneralChat: Boolean = false,

    @get:Exclude
    var role: Role = Role.USER
)