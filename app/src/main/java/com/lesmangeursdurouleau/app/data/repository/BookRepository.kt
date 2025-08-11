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
    fun getBooksByIds(bookIds: List<String>): Flow<Resource<List<Book>>>
    suspend fun addBook(book: Book): Resource<String>
    suspend fun updateBook(book: Book): Resource<Unit>

    // --- GESTION DE LA BIBLIOTHÈQUE PERSONNELLE DE L'UTILISATEUR ---
    fun getLibraryEntriesForUser(userId: String): Flow<Resource<List<UserLibraryEntry>>>
    fun getCompletedLibraryEntriesForUser(userId: String, sortOptions: SortOptions): Flow<Resource<List<UserLibraryEntry>>>
    fun getLibraryEntry(userId: String, bookId: String): Flow<Resource<UserLibraryEntry?>>
    suspend fun updateLibraryEntry(userId: String, entry: UserLibraryEntry): Resource<Unit>
    suspend fun removeBookFromUserLibrary(userId: String, bookId: String): Resource<Unit>

    // === DÉBUT DE L'AJOUT ===
    // --- GESTION DE LA PROGRESSION DE LECTURE (LOCAL) ---

    /**
     * NOUVEAU : Sauvegarde la dernière page lue pour un utilisateur et un livre spécifiques.
     *
     * @param userId L'ID de l'utilisateur.
     * @param bookId L'ID du livre.
     * @param page Le numéro de la page à sauvegarder.
     */
    suspend fun saveReadingProgress(userId: String, bookId: String, page: Int)

    /**
     * NOUVEAU : Récupère la dernière page lue pour un utilisateur et un livre spécifiques.
     *
     * @param userId L'ID de l'utilisateur.
     * @param bookId L'ID du livre.
     * @return Le numéro de la dernière page sauvegardée, ou 0 si aucune n'est trouvée.
     */
    suspend fun getReadingProgress(userId: String, bookId: String): Int
    // === FIN DE L'AJOUT ===
}