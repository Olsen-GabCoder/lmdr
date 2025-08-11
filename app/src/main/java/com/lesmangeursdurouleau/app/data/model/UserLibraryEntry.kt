package com.lesmangeursdurouleau.app.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize

enum class ReadingStatus {
    @PropertyName("WANT_TO_READ")
    TO_READ,
    READING,
    FINISHED
}

@Parcelize
data class UserLibraryEntry(
    val bookId: String = "",
    val userId: String = "",
    var status: ReadingStatus = ReadingStatus.TO_READ,
    var currentPage: Int = 0,
    val totalPages: Int = 0,

    // === DÉBUT DE LA MODIFICATION (DÉNORMALISATION) ===
    // JUSTIFICATION : Ces champs sont une copie des données du livre principal.
    // Les ajouter ici permet d'effectuer des requêtes et des tris très rapides
    // directement sur la bibliothèque de l'utilisateur, sans avoir besoin de
    // charger les informations du livre séparément. C'est crucial pour la performance.
    val bookTitle: String = "",
    val bookAuthor: String = "",
    val bookCoverImageUrl: String? = null,
    // === FIN DE LA MODIFICATION ===

    @ServerTimestamp val addedDate: Timestamp? = null,
    var lastReadDate: Timestamp? = null,
    var favoriteQuote: String? = null,
    var personalReflection: String? = null
) : Parcelable