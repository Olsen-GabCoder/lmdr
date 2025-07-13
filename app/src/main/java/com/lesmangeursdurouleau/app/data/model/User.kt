// Fichier complet et corrigé : app/src/main/java/com/lesmangeursdurouleau/app/data/model/User.kt

package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class UserListItem(
    // JUSTIFICATION DE LA VÉRIFICATION : Le uid est déjà un 'val', ce qui est correct.
    // Aucune modification n'est nécessaire ici, mais nous confirmons sa conformité.
    val uid: String = "",
    val username: String = "",
    val profilePictureUrl: String? = null
)

data class User(
    /**
     * JUSTIFICATION DE LA MODIFICATION : Le mot-clé est changé de `var` à `val`.
     * Cette modification cruciale rend l'identifiant unique de l'utilisateur immuable après sa création.
     * Cela renforce l'intégrité du modèle de données, prévient les modifications accidentelles
     * et résout la faille de modélisation 🔒, alignant le code sur les meilleures pratiques de conception.
     */
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val profilePictureUrl: String? = null,

    @ServerTimestamp
    val createdAt: Date? = null,

    val bio: String? = null,
    val city: String? = null,
    val canEditReadings: Boolean = false,
    val lastPermissionGrantedTimestamp: Long? = null,

    // Ces compteurs sont gérés par des transactions côté serveur, mais il est plus sûr
    // de les avoir en 'val' côté client et de recevoir l'objet complet mis à jour.
    // Cependant, pour ne pas casser une logique de mise à jour optimiste potentielle,
    // on les laisse en 'var' pour le moment, mais cela pourrait être un point d'amélioration futur.
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