package com.lesmangeursdurouleau.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_comments")
data class HiddenCommentEntity(
    @PrimaryKey
    val commentId: String
)