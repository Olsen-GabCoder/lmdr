package com.lesmangeursdurouleau.app.domain.usecase.library

import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case pour récupérer en temps réel toutes les entrées de la bibliothèque d'un utilisateur.
 */
class GetLibraryEntriesUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    operator fun invoke(userId: String): Flow<Resource<List<UserLibraryEntry>>> {
        return bookRepository.getLibraryEntriesForUser(userId)
    }
}