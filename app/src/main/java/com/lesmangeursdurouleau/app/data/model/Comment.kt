// Fichier complet : Comment.kt

package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Représente un commentaire sur la lecture terminée ou active d'un utilisateur.
 * Le modèle supporte maintenant les réponses imbriquées.
 */
data class Comment(
    @DocumentId
    val commentId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val bookId: String = "",
    val commentText: String = "",
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    val likesCount: Int = 0,
    val lastLikeTimestamp: Timestamp? = null,
    val isEdited: Boolean = false,
    // JUSTIFICATION DE L'AJOUT : Ce champ est ajouté pour synchroniser le modèle de données client avec le schéma de la base Firestore,
    // comme l'indiquent les avertissements du logcat. Son ajout résout la perte de données à la désérialisation.
    // Il est nullable (`Timestamp?`) pour garantir la rétrocompatibilité avec les documents de commentaires plus anciens
    // qui pourraient ne pas avoir ce champ.
    @ServerTimestamp
    val lastEditTimestamp: Timestamp? = null,
    val parentCommentId: String? = null,
    val replyCount: Int = 0
)