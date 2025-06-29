// app/src/main/java/com/lesmangeursdurouleau/app/data/model/Book.kt
package com.lesmangeursdurouleau.app.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Book(
    // Champs existants
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val coverImageUrl: String? = null,
    val synopsis: String? = null,
    val totalPages: Int = 0,

    // Nouveaux champs pour la lecture numérique et les métadonnées enrichies
    val contentUrl: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val publicationDate: String? = null,
    val isbn: String? = null,
    val genre: String? = null

) : Parcelable