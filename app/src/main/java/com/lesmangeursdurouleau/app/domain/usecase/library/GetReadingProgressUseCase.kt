package com.lesmangeursdurouleau.app.domain.usecase.library

import com.lesmangeursdurouleau.app.data.repository.BookRepository
import javax.inject.Inject

/**
 * Use Case pour récupérer la progression de lecture (la dernière page lue)
 * pour un livre et un utilisateur donnés.
 *
 * Encapsule la logique de récupération de cette information, en la décorrélant
 * de la source de données (SharedPreferences, base de données, etc.).
 */
class GetReadingProgressUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    /**
     * Exécute le cas d'utilisation.
     * @param userId L'identifiant de l'utilisateur.
     * @param bookId L'identifiant du livre.
     * @return Le numéro de la page sauvegardée (0 par défaut).
     */
    suspend operator fun invoke(userId: String, bookId: String): Int {
        return bookRepository.getReadingProgress(userId, bookId)
    }
}