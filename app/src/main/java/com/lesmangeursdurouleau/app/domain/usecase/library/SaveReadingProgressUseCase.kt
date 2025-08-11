package com.lesmangeursdurouleau.app.domain.usecase.library

import com.lesmangeursdurouleau.app.data.repository.BookRepository
import javax.inject.Inject

/**
 * Use Case pour sauvegarder la progression de lecture (la dernière page lue)
 * pour un livre et un utilisateur donnés.
 *
 * Encapsule la logique de sauvegarde, permettant au ViewModel de simplement
 * dire "sauvegarde cette page" sans connaître les détails de l'implémentation.
 */
class SaveReadingProgressUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    /**
     * Exécute le cas d'utilisation.
     * @param userId L'identifiant de l'utilisateur.
     * @param bookId L'identifiant du livre.
     * @param page Le numéro de la page à sauvegarder.
     */
    suspend operator fun invoke(userId: String, bookId: String, page: Int) {
        bookRepository.saveReadingProgress(userId, bookId, page)
    }
}