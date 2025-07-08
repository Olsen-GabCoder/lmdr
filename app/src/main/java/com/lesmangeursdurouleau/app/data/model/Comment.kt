// PRÊT À COLLER - Fichier Comment.kt complet et MODIFIÉ
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

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
    val targetUserId: String = "",
    val bookId: String = "",
    val commentText: String = "",
    val timestamp: Timestamp = Timestamp.now(),

    // Champs pour les interactions
    val likesCount: Int = 0,
    val lastLikeTimestamp: Timestamp? = null,

    // AJOUT : Champs pour gérer les réponses aux commentaires (threading)
    /** L'ID du commentaire parent. Null si c'est un commentaire de premier niveau. */
    val parentCommentId: String? = null,
    /** Le nombre de réponses directes à ce commentaire. */
    val replyCount: Int = 0
)