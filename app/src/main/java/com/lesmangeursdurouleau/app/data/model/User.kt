// Fichier Modifié : app/src/main/java/com/lesmangeursdurouleau/app/data/model/User.kt

package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * JUSTIFICATION DE LA CRÉATION : Ce nouveau modèle de données est la pierre angulaire de l'optimisation.
 * Il ne contient que les 3 champs strictement nécessaires à l'affichage dans la liste des membres.
 * Utiliser ce modèle au lieu de l'objet `User` complet réduit drastiquement la quantité de données
 * lues depuis Firestore pour chaque item de la liste, optimisant ainsi les coûts et la performance.
 */
data class UserListItem(
    val uid: String = "",
    val username: String = "",
    val profilePictureUrl: String? = null
)

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

    val isEmailVerified: Boolean = false,

    val fcmToken: String? = null,
    val isTypingInGeneralChat: Boolean = false
)