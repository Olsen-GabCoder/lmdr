// PRÊT À COLLER - Créez un nouveau fichier GetLibraryEntriesUseCase.kt
package com.lesmangeursdurouleau.app.domain.usecase.library

import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case pour récupérer en temps réel la liste complète des entrées
 * de la bibliothèque personnelle d'un utilisateur.
 *
 * Il sert de pont entre le ViewModel et le BookRepository, en encapsulant
 * la logique métier de cette récupération de données.
 */
class GetLibraryEntriesUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    /**
     * Exécute le cas d'utilisation.
     * @param userId L'identifiant de l'utilisateur dont on veut la bibliothèque.
     * @return Un [Flow] de [Resource] qui émet la liste des [UserLibraryEntry].
     */
    operator fun invoke(userId: String): Flow<Resource<List<UserLibraryEntry>>> {
        return bookRepository.getLibraryEntriesForUser(userId)
    }
}