// PRÊT À COLLER - Remplacez tout le contenu de votre fichier CheckBookInLibraryUseCase.kt
package com.lesmangeursdurouleau.app.domain.usecase.books

import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case pour vérifier si un livre est présent dans la bibliothèque d'un utilisateur
 * et observer son statut en temps réel.
 * Encapsule la logique métier pour cette interrogation spécifique.
 */
class CheckBookInLibraryUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    /**
     * Exécute le use case.
     * @param userId L'identifiant de l'utilisateur.
     * @param bookId L'identifiant du livre à vérifier.
     * @return Un [Flow] de [Resource] qui émet `true` si le livre est dans la bibliothèque,
     * `false` sinon, et gère les états de chargement et d'erreur.
     */
    operator fun invoke(userId: String, bookId: String): Flow<Resource<Boolean>> {
        // Appelle la nouvelle méthode du repository qui retourne l'entrée complète.
        return bookRepository.getLibraryEntry(userId, bookId).map { resource ->
            // Transforme le résultat pour correspondre au contrat du Use Case.
            when (resource) {
                is Resource.Success -> Resource.Success(resource.data != null)
                is Resource.Error -> Resource.Error(resource.message ?: "Erreur inconnue")
                is Resource.Loading -> Resource.Loading()
            }
        }
    }
}