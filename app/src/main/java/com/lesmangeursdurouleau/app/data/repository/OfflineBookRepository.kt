// PRÊT À COLLER - Créez ce nouveau fichier : OfflineBookRepository.kt
package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Définit le contrat pour la gestion des livres stockés localement pour un accès hors-ligne.
 */
interface OfflineBookRepository {

    /**
     * Vérifie si un livre est actuellement téléchargé et disponible hors-ligne.
     * @param bookId L'ID du livre à vérifier.
     * @return Un Flow qui émet `true` si le livre est téléchargé, `false` sinon.
     */
    fun isBookDownloaded(bookId: String): Flow<Boolean>

    /**
     * Récupère le fichier local d'un livre s'il est téléchargé.
     * @param bookId L'ID du livre.
     * @return Le `File` local ou `null` si non disponible.
     */
    suspend fun getBookFile(bookId: String): File?

    /**
     * Lance le téléchargement d'un livre pour le rendre disponible hors-ligne.
     * @param bookId L'ID du livre.
     * @param pdfUrl L'URL de téléchargement du PDF.
     * @return Un Resource indiquant le succès ou l'échec de l'opération.
     */
    suspend fun downloadBook(bookId: String, pdfUrl: String): Resource<Unit>

    /**
     * Supprime un livre du stockage hors-ligne.
     * @param bookId L'ID du livre à supprimer.
     * @return Un Resource indiquant le succès ou l'échec de l'opération.
     */
    suspend fun deleteBook(bookId: String): Resource<Unit>
}