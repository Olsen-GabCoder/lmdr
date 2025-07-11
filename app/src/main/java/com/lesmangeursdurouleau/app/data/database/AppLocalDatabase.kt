// PRÊT À COLLER - Fichier AppLocalDatabase.kt complet et CORRIGÉ
package com.lesmangeursdurouleau.app.data.database

// AJOUT : Imports manquants pour résoudre les erreurs de référence.
import androidx.room.Database
import androidx.room.RoomDatabase
import com.lesmangeursdurouleau.app.data.database.dao.HiddenCommentDao
import com.lesmangeursdurouleau.app.data.database.entity.HiddenCommentEntity

@Database(
    entities = [HiddenCommentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppLocalDatabase : RoomDatabase() {
    abstract fun hiddenCommentDao(): HiddenCommentDao
}