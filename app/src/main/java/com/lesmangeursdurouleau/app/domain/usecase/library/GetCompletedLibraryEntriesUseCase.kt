package com.lesmangeursdurouleau.app.domain.usecase.library

import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.ui.members.SortOptions
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case pour récupérer en temps réel la liste des lectures terminées
 * pour un utilisateur, en utilisant une requête Firestore optimisée.
 */
class GetCompletedLibraryEntriesUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    /**
     * Exécute le cas d'utilisation.
     * @param userId L'identifiant de l'utilisateur.
     * @param sortOptions Les options de tri à appliquer côté serveur.
     * @return Un [Flow] de [Resource] qui émet la liste des [UserLibraryEntry] terminées.
     */
    operator fun invoke(userId: String, sortOptions: SortOptions): Flow<Resource<List<UserLibraryEntry>>> {
        return bookRepository.getCompletedLibraryEntriesForUser(userId, sortOptions)
    }
}