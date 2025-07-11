// PRÊT À COLLER - Fichier HiddenCommentDao.kt complet et MODIFIÉ
package com.lesmangeursdurouleau.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lesmangeursdurouleau.app.data.database.entity.HiddenCommentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenCommentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(hiddenComment: HiddenCommentEntity)

    // AJOUT : Nouvelle méthode pour supprimer un commentaire masqué par son ID.
    @Query("DELETE FROM hidden_comments WHERE commentId = :commentId")
    suspend fun unhide(commentId: String)

    @Query("SELECT commentId FROM hidden_comments")
    fun getHiddenCommentIds(): Flow<List<String>>

}