package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Encapsule une page de résultats de commentaires et le curseur pour la requête suivante.
 * @param comments La liste des commentaires pour la page actuelle.
 * @param lastDocumentSnapshot Le DocumentSnapshot du dernier commentaire de la liste.
 *                            Sert de curseur pour la fonction startAfter() de Firestore.
 *                            Est null si c'est la dernière page.
 */
data class CommentPage(
    val comments: List<Comment>,
    val lastDocumentSnapshot: DocumentSnapshot?
)