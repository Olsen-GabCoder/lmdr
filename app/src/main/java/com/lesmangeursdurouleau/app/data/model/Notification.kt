// PRÊT À COLLER - Fichier Notification.kt
package com.lesmangeursdurouleau.app.data.model // ou com.lesmangeursdurouleau.app.notifications.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Représente une seule notification dans la "boîte de réception" d'un utilisateur dans Firestore.
 * C'est le modèle de données utilisé pour l'affichage dans la liste des notifications.
 */
data class Notification(
    /** L'ID unique du document de notification, généré par Firestore. */
    @DocumentId
    val id: String = "",

    /** L'ID de l'utilisateur qui doit recevoir cette notification. */
    val recipientId: String = "",

    /** L'ID de l'utilisateur qui a initié l'action (ex: celui qui a suivi, liké, etc.). */
    val actorId: String = "",

    /** Le nom d'utilisateur de l'acteur, pour un affichage direct sans requête supplémentaire. */
    val actorUsername: String = "",

    /** L'URL de la photo de profil de l'acteur, pour un affichage direct. */
    val actorProfilePictureUrl: String? = null,

    /** Le type de notification, qui dicte le message et l'icône à afficher. */
    val type: NotificationType = NotificationType.UNKNOWN,

    /**
     * L'ID de l'entité concernée par la notification.
     * - Pour LIKE_ON_READING, c'est le `bookId`.
     * - Pour COMMENT_ON_READING, c'est le `bookId`.
     * - Pour NEW_FOLLOWER, peut être vide ou contenir `actorId`.
     * NOTE: Ce champ est conservé pour la compatibilité, mais la nouvelle logique privilégiera les champs plus spécifiques.
     */
    val entityId: String = "",

    /**
     * Un titre ou un extrait de l'entité, pour un affichage riche.
     * - Pour un commentaire, ce pourrait être l'extrait du commentaire.
     * - Pour une lecture, le titre du livre.
     */
    val entityTitle: String? = null,

    /** L'horodatage de l'événement, fourni par le serveur. */
    @ServerTimestamp
    val timestamp: Date? = null,

    /** Un booléen pour savoir si l'utilisateur a déjà vu cette notification. */
    var isRead: Boolean = false,

    // ---- AJOUTS POUR UNE NAVIGATION PRÉCISE (Chantier 2) ----

    /**
     * L'ID de l'utilisateur dont le contenu est la cible de l'interaction.
     * Pour un commentaire ou un like sur une lecture, c'est l'ID du propriétaire de la lecture.
     * Ce champ définit la destination de la navigation (ex: le profil à afficher).
     */
    val targetUserId: String? = null,

    /**
     * L'ID du commentaire spécifique concerné par la notification, s'il y a lieu.
     * Permet de scroller directement vers le bon commentaire dans la liste.
     */
    val commentId: String? = null
)