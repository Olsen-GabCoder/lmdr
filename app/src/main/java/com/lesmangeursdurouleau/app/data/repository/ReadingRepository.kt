// PRÊT À COLLER - Remplacez tout le contenu de votre fichier ReadingRepository.kt
package com.lesmangeursdurouleau.app.data.repository

import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository dédié EXCLUSIVEMENT à la gestion des activités de lecture des utilisateurs
 * (lecture active et historique des lectures terminées).
 * La logique sociale (commentaires, likes) a été déplacée dans un repository dédié.
 */
@Deprecated(
    message = "Ce repository est obsolète. La logique de gestion de la bibliothèque personnelle a été unifiée dans BookRepository en utilisant le modèle UserLibraryEntry.",
    replaceWith = ReplaceWith("BookRepository", "com.lesmangeursdurouleau.app.data.repository.BookRepository")
)
interface ReadingRepository {

    /**
     * Récupère en temps réel la lecture en cours d'un utilisateur.
     * @param userId L'ID de l'utilisateur.
     * @return Un Flow de Resource contenant les détails de la lecture en cours, ou null si aucune.
     */
    fun getCurrentReading(userId: String): Flow<Resource<UserBookReading?>>

    /**
     * Met à jour ou supprime la lecture en cours d'un utilisateur.
     * @param userId L'ID de l'utilisateur.
     * @param userBookReading L'objet de lecture à enregistrer, ou null pour la supprimer.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun updateCurrentReading(userId: String, userBookReading: UserBookReading?): Resource<Unit>

    /**
     * Marque une lecture active comme terminée, créant une entrée dans l'historique de l'utilisateur.
     * @param userId L'ID de l'utilisateur.
     * @param activeReadingDetails Les détails de la lecture à marquer comme terminée.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun markActiveReadingAsCompleted(userId: String, activeReadingDetails: UserBookReading): Resource<Unit>

    /**
     * Supprime une lecture de la liste des lectures terminées.
     * @param userId L'ID de l'utilisateur.
     * @param bookId L'ID du livre à supprimer.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun removeCompletedReading(userId: String, bookId: String): Resource<Unit>

    /**
     * Récupère en temps réel la liste des lectures terminées d'un utilisateur, avec tri.
     * @param userId L'ID de l'utilisateur.
     * @param orderBy Le champ sur lequel trier.
     * @param direction La direction du tri (ascendant/descendant).
     * @return Un Flow de Resource contenant la liste des lectures terminées.
     */
    fun getCompletedReadings(
        userId: String,
        orderBy: String,
        direction: Query.Direction
    ): Flow<Resource<List<CompletedReading>>>

    /**
     * Récupère les détails d'une lecture terminée spécifique.
     * @param userId L'ID de l'utilisateur.
     * @param bookId L'ID du livre.
     * @return Un Flow de Resource contenant les détails de la lecture terminée, ou null.
     */
    fun getCompletedReadingDetail(userId: String, bookId: String): Flow<Resource<CompletedReading?>>

    // === TOUTES LES MÉTHODES SOCIALES ONT ÉTÉ SUPPRIMÉES DE CETTE INTERFACE ===
    // (addComment, getComments, toggleLike, etc.)
}