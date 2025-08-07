// PRÊT À COLLER - Remplacez tout le contenu de votre fichier User.kt
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.Exclude
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
    val coverPictureUrl: String? = null,

    @ServerTimestamp
    val createdAt: Date? = null,

    val bio: String? = null,
    val city: String? = null,

    // DÉPRÉCIÉ : Ce champ est remplacé par le nouveau système de rôles.
    // Nous le conservons temporairement pour la compatibilité le temps de la transition.
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
    val isTypingInGeneralChat: Boolean = false,

    // JUSTIFICATION DE L'AJOUT : Ce champ stockera le rôle de l'utilisateur.
    // Il est lu depuis les "custom claims" du token d'authentification et non depuis Firestore,
    // c'est pourquoi il est annoté avec @get:Exclude. Cela l'empêche d'être écrit dans la
    // base de données Firestore lors des mises à jour du profil, ce qui est crucial pour la sécurité.
    // Le rôle est la source de vérité pour les permissions d'administration.
    @get:Exclude
    var role: Role = Role.USER
)