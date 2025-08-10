// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier OfflineBookRepositoryImpl.kt
package com.lesmangeursdurouleau.app.data.repository

import android.content.Context
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineBookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : OfflineBookRepository {

    private val offlineBooksDir = File(context.filesDir, "offline_books")
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // === DÉBUT DE LA MODIFICATION ===
    // Map pour conserver en mémoire l'état de téléchargement de chaque livre observé.
    // ConcurrentHashMap est utilisé pour la sécurité dans un environnement multithread.
    private val downloadStateFlows = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
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
     * MODIFIÉ: La fonction retourne maintenant un StateFlow réactif.
     * Elle crée un StateFlow à la volée s'il n'existe pas pour un bookId donné,
     * puis le met à jour, assurant que les changements sont propagés instantanément.
     */
    override fun isBookDownloaded(bookId: String): Flow<Boolean> {
        return downloadStateFlows.getOrPut(bookId) {
            // Si le flow n'existe pas, on le crée.
            // On vérifie l'état initial sur le disque.
            val initialState = getFileForBook(bookId).exists()
            MutableStateFlow(initialState)
        }.asStateFlow()
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
                // === DÉBUT DE LA MODIFICATION ===
                // On notifie les observateurs que le livre est maintenant téléchargé.
                downloadStateFlows[bookId]?.value = true
                // === FIN DE LA MODIFICATION ===
                Resource.Success(Unit)
            } catch (e: Exception) {
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
                // === DÉBUT DE LA MODIFICATION ===
                // On notifie les observateurs que le livre n'est plus téléchargé.
                downloadStateFlows[bookId]?.value = false
                // === FIN DE LA MODIFICATION ===
                Resource.Success(Unit)
            } catch (e: Exception) {
                Resource.Error("Échec de la suppression: ${e.message}")
            }
        }
    }
}