// PRÊT À COLLER - Remplacez tout le contenu de votre fichier Book.kt par ceci.
package com.lesmangeursdurouleau.app.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Modèle de données pour un livre.
 * Cette version est une réplique exacte de la structure des documents dans Firestore pour garantir une désérialisation sans erreur.
 */
@Parcelize
data class Book(
    // CORRIGÉ : L'annotation @DocumentId a été retirée pour résoudre le conflit.
    // Votre document Firestore contient déjà un champ 'id'. L'ID réel du document sera
    // assigné manuellement dans le Repository pour garantir la cohérence.
    var id: String = "",

    val title: String = "",
    val author: String = "",
    val coverImageUrl: String? = null,
    val synopsis: String? = null,
    val totalPages: Int = 0,

    val contentUrl: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val publicationDate: String? = null,
    val isbn: String? = null,
    val genre: String? = null,

    // Le @JvmField est conservé car il résout un autre problème potentiel ("conflicting getters").
    @JvmField
    val stability: Long? = 0

) : Parcelable