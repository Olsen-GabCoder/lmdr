// PRÊT À COLLER - Remplacez tout le contenu de votre fichier User.kt par ceci.
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    var uid: String = "",
    val username: String = "",
    val email: String = "",
    val profilePictureUrl: String? = null,
    val createdAt: Long? = null,
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

    // === DÉBUT DES AJOUTS ===
    // C'est l'ajout de ces lignes qui va résoudre vos erreurs.
    /**
     * Le score d'affinité le plus élevé que cet utilisateur a atteint avec n'importe qui.
     */
    val highestAffinityScore: Int = 0,

    /**
     * L'ID de l'utilisateur avec qui le score d'affinité le plus élevé est atteint.
     */
    val highestAffinityPartnerId: String? = null,

    /**
     * Le nom d'utilisateur du partenaire (dénormalisé pour un affichage direct et performant).
     */
    val highestAffinityPartnerUsername: String? = null,

    /**
     * Le titre du palier d'affinité le plus élevé atteint (ex: "Complice littéraire").
     */
    val highestAffinityTierName: String? = null
    // === FIN DES AJOUTS ===
)