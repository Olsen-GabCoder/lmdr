package com.lesmangeursdurouleau.app.domain.usecase.books

import com.google.firebase.Timestamp
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.ReadingStatus
import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
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
        if (userId.isBlank() || book.id.isBlank()) {
            return Resource.Error("L'ID de l'utilisateur et du livre sont requis.")
        }

        // Création de l'entrée de bibliothèque initiale.
        val newLibraryEntry = UserLibraryEntry(
            // Informations de base
            bookId = book.id,
            userId = userId,
            status = ReadingStatus.TO_READ, // Statut par défaut à l'ajout
            currentPage = 0,
            totalPages = book.totalPages,
            lastReadDate = Timestamp.now(), // Initialise la date pour le tri

            // === DÉBUT DE LA MODIFICATION (DÉNORMALISATION) ===
            // JUSTIFICATION : On copie les données du livre directement dans l'entrée
            // de la bibliothèque. C'est le cœur de la dénormalisation.
            // Cela rendra les lectures et les tris futurs beaucoup plus performants.
            bookTitle = book.title,
            bookAuthor = book.author,
            bookCoverImageUrl = book.coverImageUrl
            // === FIN DE LA MODIFICATION ===
        )

        // Utilise la méthode unifiée de mise à jour/création.
        return bookRepository.updateLibraryEntry(userId, newLibraryEntry)
    }
}