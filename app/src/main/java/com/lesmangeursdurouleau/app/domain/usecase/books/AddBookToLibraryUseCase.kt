// app/src/main/java/com/lesmangeursdurouleau/app/domain/usecase/book/AddBookToLibraryUseCase.kt
package com.lesmangeursdurouleau.app.domain.usecase.books

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import javax.inject.Inject

/**
 * Use case pour ajouter un livre à la bibliothèque personnelle d'un utilisateur.
 * Encapsule la logique métier pour cette action spécifique.
 */
class AddBookToLibraryUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {

    /**
     * Exécute le use case.
     * @param userId L'identifiant de l'utilisateur.
     * @param book Le livre à ajouter.
     * @return Un [Resource] indiquant le résultat de l'opération.
     */
    suspend operator fun invoke(userId: String, book: Book): Resource<Unit> {
        // Valider les entrées ici si nécessaire, bien que le repository le fasse déjà.
        // Pour ce cas simple, on délègue directement l'appel au repository.
        return bookRepository.addBookToUserLibrary(userId, book)
    }
}