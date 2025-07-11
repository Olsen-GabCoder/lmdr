package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.database.dao.HiddenCommentDao
import com.lesmangeursdurouleau.app.data.database.entity.HiddenCommentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalUserPreferencesRepositoryImpl @Inject constructor(
    private val hiddenCommentDao: HiddenCommentDao
) : LocalUserPreferencesRepository {

    override fun getHiddenCommentIds(): Flow<List<String>> {
        return hiddenCommentDao.getHiddenCommentIds()
    }

    override suspend fun hideComment(commentId: String) {
        val hiddenComment = HiddenCommentEntity(commentId = commentId)
        hiddenCommentDao.hide(hiddenComment)
    }

    override suspend fun unhideComment(commentId: String) {
        hiddenCommentDao.unhide(commentId)
    }
}