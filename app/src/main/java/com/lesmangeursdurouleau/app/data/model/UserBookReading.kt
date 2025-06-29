// app/src/main/java/com/lesmangeursdurouleau.app/data/model/UserBookReading.kt
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserBookReading(
    val bookId: String = "",
    val title: String = "",
    val author: String = "",
    val coverImageUrl: String? = null,
    val status: String = "in_progress",
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val startedReadingAt: Long? = null,
    val finishedReadingAt: Long? = null,
    val lastPageUpdateAt: Long? = null,
    val favoriteQuote: String? = null,
    val personalReflection: String? = null,
    val rating: Int? = null,
    val review: String? = null
)