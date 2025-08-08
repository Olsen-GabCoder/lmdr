// PRÊT À COLLER - Remplacez tout le contenu de votre fichier UserLibraryEntry.kt
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
    @ServerTimestamp val addedDate: Timestamp? = null,
    var lastReadDate: Timestamp? = null,
    // AJOUT : Rétablissement des champs pour les notes et citations.
    var favoriteQuote: String? = null,
    var personalReflection: String? = null
) : Parcelable