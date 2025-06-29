// app/src/main/java/com/lesmangeursdurouleau/app/data/repository/BookRepository.kt
package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<Resource<List<Book>>>
    fun getBookById(bookId: String): Flow<Resource<Book?>>
    suspend fun addBook(book: Book): Resource<String>
    suspend fun updateBook(book: Book): Resource<Unit>

    // --- NOUVELLES FONCTIONS POUR LA BIBLIOTHÈQUE PERSONNELLE ---

    /**
     * Ajoute un livre à la sous-collection "library" d'un utilisateur spécifique.
     * @param userId L'ID de l'utilisateur.
     * @param book L'objet Book complet à ajouter.
     * @return Resource<Unit> indiquant le succès ou l'échec de l'opération.
     */
    suspend fun addBookToUserLibrary(userId: String, book: Book): Resource<Unit>

    /**
     * Vérifie si un livre existe déjà dans la bibliothèque d'un utilisateur et observe son état.
     * @param userId L'ID de l'utilisateur.
     * @param bookId L'ID du livre à vérifier.
     * @return Un Flow qui émet true si le livre est dans la bibliothèque, sinon false.
     */
    fun isBookInUserLibrary(userId: String, bookId: String): Flow<Resource<Boolean>>
}