package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.ui.members.SortOptions
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    // --- GESTION DU CATALOGUE GÉNÉRAL DE LIVRES ---
    fun getAllBooks(): Flow<Resource<List<Book>>>
    fun getBookById(bookId: String): Flow<Resource<Book?>>
    /**
     * NOUVEAU: Récupère une liste de livres en se basant sur leurs IDs.
     * C'est une méthode très efficace pour charger uniquement les livres nécessaires.
     *
     * @param bookIds La liste des identifiants des livres à récupérer.
     * @return Un Flow de Resource contenant la liste des livres correspondants.
     */
    fun getBooksByIds(bookIds: List<String>): Flow<Resource<List<Book>>>
    suspend fun addBook(book: Book): Resource<String>
    suspend fun updateBook(book: Book): Resource<Unit>

    // --- GESTION DE LA BIBLIOTHÈQUE PERSONNELLE DE L'UTILISATEUR (UNIFIÉE) ---

    fun getLibraryEntriesForUser(userId: String): Flow<Resource<List<UserLibraryEntry>>>

    fun getCompletedLibraryEntriesForUser(userId: String, sortOptions: SortOptions): Flow<Resource<List<UserLibraryEntry>>>

    fun getLibraryEntry(userId: String, bookId: String): Flow<Resource<UserLibraryEntry?>>

    suspend fun updateLibraryEntry(userId: String, entry: UserLibraryEntry): Resource<Unit>

    suspend fun removeBookFromUserLibrary(userId: String, bookId: String): Resource<Unit>
}