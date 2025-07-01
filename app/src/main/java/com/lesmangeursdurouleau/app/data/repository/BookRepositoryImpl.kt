// PRÊT À COLLER - Remplacez tout le contenu de votre fichier BookRepositoryImpl.kt par ceci.
package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.ReadingStatus
import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : BookRepository {

    companion object {
        private const val TAG = "BookRepositoryImpl"
        private const val COLLECTION_LIBRARY = "library"
    }

    private val booksCollection = firestore.collection(FirebaseConstants.COLLECTION_BOOKS)
    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    override fun getAllBooks(): Flow<Resource<List<Book>>> = callbackFlow {
        trySend(Resource.Loading())
        val listenerRegistration = booksCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val books = snapshot.documents.mapNotNull { document ->
                            // Désérialiser chaque document individuellement pour une meilleure gestion des erreurs.
                            document.toObject(Book::class.java)?.apply { id = document.id }
                        }
                        trySend(Resource.Success(books))
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur de désérialisation dans getAllBooks", e)
                        trySend(Resource.Error("Erreur de conversion des données: ${e.localizedMessage}"))
                    }
                } else {
                    trySend(Resource.Success(emptyList()))
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    override fun getBookById(bookId: String): Flow<Resource<Book?>> = callbackFlow {
        trySend(Resource.Loading(null))
        val documentRef = booksCollection.document(bookId)
        val listenerRegistration = documentRef.addSnapshotListener { documentSnapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            if (documentSnapshot != null && documentSnapshot.exists()) {
                try {
                    // La conversion se fait ici, avec le nouveau modèle `Book.kt` synchronisé.
                    val book = documentSnapshot.toObject(Book::class.java)?.apply { id = documentSnapshot.id }
                    trySend(Resource.Success(book))
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur de désérialisation pour le livre ID $bookId", e)
                    trySend(Resource.Error("Erreur de conversion des données du livre: ${e.localizedMessage}"))
                }
            } else {
                trySend(Resource.Success(null))
            }
        }
        awaitClose { listenerRegistration.remove() }
    }

    override suspend fun addBook(book: Book): Resource<String> {
        return try {
            val bookToAdd = book.copy(id = "") // S'assurer que l'ID n'est pas envoyé
            val documentRef = booksCollection.add(bookToAdd).await()
            Resource.Success(documentRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding book: ${book.title}", e)
            Resource.Error("Erreur lors de l'ajout du livre: ${e.localizedMessage}")
        }
    }

    override suspend fun updateBook(book: Book): Resource<Unit> {
        if (book.id.isBlank()) {
            return Resource.Error("L'ID du livre est requis pour la mise à jour.")
        }
        return try {
            // L'objet Book est directement sérialisé par Firestore.
            booksCollection.document(book.id).set(book).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating book ${book.id}: $e", e)
            Resource.Error("Erreur lors de la mise à jour du livre: ${e.localizedMessage}")
        }
    }

    override suspend fun addBookToUserLibrary(userId: String, book: Book): Resource<Unit> {
        if (userId.isBlank() || book.id.isBlank()) {
            return Resource.Error("User ID and Book ID cannot be blank.")
        }
        return try {
            val libraryEntry = UserLibraryEntry(
                bookId = book.id,
                userId = userId,
                status = ReadingStatus.TO_READ,
                currentPage = 0,
                totalPages = book.totalPages
            )
            usersCollection.document(userId)
                .collection(COLLECTION_LIBRARY)
                .document(book.id)
                .set(libraryEntry)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding book ${book.id} to user $userId library", e)
            Resource.Error("Erreur lors de l'ajout du livre à la bibliothèque: ${e.localizedMessage}")
        }
    }

    override fun isBookInUserLibrary(userId: String, bookId: String): Flow<Resource<Boolean>> = callbackFlow {
        if (userId.isBlank() || bookId.isBlank()) {
            trySend(Resource.Error("User ID and Book ID cannot be blank."))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        val docRef = usersCollection.document(userId).collection(COLLECTION_LIBRARY).document(bookId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            trySend(Resource.Success(snapshot != null && snapshot.exists()))
        }
        awaitClose { listener.remove() }
    }
}