// app/src/main/java/com/lesmangeursdurouleau/app/domain/usecase/book/CheckBookInLibraryUseCase.kt
package com.lesmangeursdurouleau.app.domain.usecase.books

import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
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
        return bookRepository.isBookInUserLibrary(userId, bookId)
    }
}