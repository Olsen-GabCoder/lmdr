package com.lesmangeursdurouleau.app.domain.usecase.library

import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case pour récupérer en temps réel UNE SEULE entrée de bibliothèque spécifique
 * pour un utilisateur donné, en utilisant son ID et l'ID du livre.
 */
class GetLibraryEntryUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    /**
     * Exécute le cas d'utilisation.
     * @param userId L'identifiant de l'utilisateur.
     * @param bookId L'identifiant du livre.
     * @return Un [Flow] de [Resource] qui émet l'objet [UserLibraryEntry] ou null s'il n'existe pas.
     */
    operator fun invoke(userId: String, bookId: String): Flow<Resource<UserLibraryEntry?>> {
        // Appelle la méthode getLibraryEntry (au singulier) du repository
        return bookRepository.getLibraryEntry(userId, bookId)
    }
}