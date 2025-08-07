// PRÊT À COLLER - Remplacez tout le contenu de votre fichier BookRepository.kt
package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<Resource<List<Book>>>
    fun getBookById(bookId: String): Flow<Resource<Book?>>
    suspend fun addBook(book: Book): Resource<String>
    suspend fun updateBook(book: Book): Resource<Unit>

    // --- FONCTIONS POUR LA BIBLIOTHÈQUE PERSONNELLE (ÉTENDUES) ---

    suspend fun addBookToUserLibrary(userId: String, book: Book): Resource<Unit>
    fun isBookInUserLibrary(userId: String, bookId: String): Flow<Resource<Boolean>>

    /**
     * JUSTIFICATION DE L'AJOUT : Récupère en temps réel toutes les entrées de la bibliothèque d'un utilisateur.
     * C'est la fonction clé qui permettra d'alimenter l'écran de la bibliothèque que vous avez montré.
     *
     * @param userId L'ID de l'utilisateur.
     * @return Un Flow de Resource contenant la liste des entrées de la bibliothèque.
     */
    fun getLibraryEntriesForUser(userId: String): Flow<Resource<List<UserLibraryEntry>>>

    /**
     * JUSTIFICATION DE L'AJOUT : Met à jour une entrée existante dans la bibliothèque de l'utilisateur.
     * Essentiel pour sauvegarder la progression de lecture (changement de currentPage) ou
     * pour changer le statut d'un livre (par ex, de READING à FINISHED).
     *
     * @param userId L'ID de l'utilisateur.
     * @param entry L'objet UserLibraryEntry complet avec les nouvelles données.
     * @return Resource<Unit> indiquant le succès ou l'échec.
     */
    suspend fun updateLibraryEntry(userId: String, entry: UserLibraryEntry): Resource<Unit>
}