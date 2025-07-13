// PRÊT À COLLER - Fichier User.kt mis à jour
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class UserListItem(
    val uid: String = "",
    val username: String = "",
    val profilePictureUrl: String? = null
)

data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val profilePictureUrl: String? = null,

    /**
     * JUSTIFICATION DE L'AJOUT : Ajout du champ pour la photo de couverture.
     * Ce champ de type `String?` stockera l'URL de l'image de couverture de l'utilisateur dans Firestore.
     * Il est nullable pour gérer le cas où un utilisateur n'a pas encore défini de couverture.
     * C'est l'étape fondamentale qui permet d'intégrer cette nouvelle fonctionnalité dans notre modèle de données.
     */
    val coverPictureUrl: String? = null,

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