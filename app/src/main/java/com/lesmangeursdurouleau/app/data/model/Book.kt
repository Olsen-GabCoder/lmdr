// PRÊT À COLLER - Fichier Book.kt
package com.lesmangeursdurouleau.app.data.model

import android.os.Parcelable
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Book(
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
    @JvmField
    val stability: Long? = 0,

    // CORRECTION : Champs ajoutés pour correspondre à Firestore
    val likesCount: Int = 0,
    val favoritesCount: Int = 0,
    val recommendationsCount: Int = 0,

    @ServerTimestamp
    val proposedAt: Date? = null

) : Parcelable