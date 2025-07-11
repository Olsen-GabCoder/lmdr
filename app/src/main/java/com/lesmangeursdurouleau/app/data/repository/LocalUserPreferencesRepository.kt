package com.lesmangeursdurouleau.app.data.repository

import kotlinx.coroutines.flow.Flow

interface LocalUserPreferencesRepository {
    fun getHiddenCommentIds(): Flow<List<String>>
    suspend fun hideComment(commentId: String)
    suspend fun unhideComment(commentId: String)
}