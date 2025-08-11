package com.lesmangeursdurouleau.app.data.repository

import android.content.Context
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineBookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : OfflineBookRepository {

    private val offlineBooksDir = File(context.filesDir, "offline_books")

    // === DÉBUT DE LA MODIFICATION ===
    // SUPPRIMÉ : La map `downloadStateFlows` qui causait la fuite de mémoire a été retirée.
    // Le repository est maintenant sans état interne (stateless), ce qui est plus sûr pour un Singleton.
    // === FIN DE LA MODIFICATION ===

    init {
        if (!offlineBooksDir.exists()) {
            offlineBooksDir.mkdirs()
        }
    }

    private fun getFileForBook(bookId: String): File {
        return File(offlineBooksDir, "book_$bookId.pdf")
    }

    // === DÉBUT DE LA MODIFICATION ===
    /**
     * MODIFIÉ : La fonction ne dépend plus d'un cache interne.
     * Elle crée un nouveau Flow qui émet la valeur actuelle de l'existence du fichier
     * sur le disque, puis se termine. C'est léger, efficace et sans fuite de mémoire.
     */
    override fun isBookDownloaded(bookId: String): Flow<Boolean> = flow {
        emit(getFileForBook(bookId).exists())
    }
    // === FIN DE LA MODIFICATION ===

    override suspend fun getBookFile(bookId: String): File? {
        return withContext(Dispatchers.IO) {
            val file = getFileForBook(bookId)
            if (file.exists()) file else null
        }
    }

    override suspend fun downloadBook(bookId: String, pdfUrl: String): Resource<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val file = getFileForBook(bookId)
                if (file.exists()) {
                    return@withContext Resource.Success(Unit)
                }

                URL(pdfUrl).openStream().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                // SUPPRIMÉ : La notification des observateurs est retirée car le cache n'existe plus.
                // Le ViewModel est maintenant responsable de la mise à jour de son propre état.
                Resource.Success(Unit)
            } catch (e: Exception) {
                // Si le téléchargement échoue, on s'assure que le fichier partiel est supprimé.
                getFileForBook(bookId).delete()
                Resource.Error("Échec du téléchargement: ${e.message}")
            }
        }
    }

    override suspend fun deleteBook(bookId: String): Resource<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val file = getFileForBook(bookId)
                if (file.exists()) {
                    file.delete()
                }
                // SUPPRIMÉ : La notification des observateurs est retirée.
                Resource.Success(Unit)
            } catch (e: Exception) {
                Resource.Error("Échec de la suppression: ${e.message}")
            }
        }
    }
}