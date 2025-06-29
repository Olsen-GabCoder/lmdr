// app/src/main/java/com/lesmangeursdurouleau/app/data/repository/BookRepositoryImpl.kt
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
        private const val COLLECTION_LIBRARY = "library" // Constante pour la sous-collection
    }

    private val booksCollection = firestore.collection(FirebaseConstants.COLLECTION_BOOKS)
    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    override fun getAllBooks(): Flow<Resource<List<Book>>> = callbackFlow {
        trySend(Resource.Loading())
        val listenerRegistration = booksCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening for all books updates", error)
                    trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    // Utilisation de .toObject() pour une conversion plus robuste et complète
                    val books = snapshot.toObjects(Book::class.java).mapIndexed { index, book ->
                        book.copy(id = snapshot.documents[index].id)
                    }
                    Log.d(TAG, "All Books fetched: ${books.size}")
                    trySend(Resource.Success(books))
                } else {
                    Log.d(TAG, "getAllBooks snapshot is null")
                    trySend(Resource.Success(emptyList()))
                }
            }
        awaitClose {
            Log.d(TAG, "Closing all books listener.")
            listenerRegistration.remove()
        }
    }

    override fun getBookById(bookId: String): Flow<Resource<Book?>> = callbackFlow {
        trySend(Resource.Loading(null))
        Log.d(TAG, "Fetching book with ID: $bookId")
        val documentRef = booksCollection.document(bookId)
        val listenerRegistration = documentRef.addSnapshotListener { documentSnapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error listening for book ID $bookId updates", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
                return@addSnapshotListener
            }
            if (documentSnapshot != null && documentSnapshot.exists()) {
                try {
                    // Utilisation de toObject pour bénéficier de la conversion automatique de Firestore
                    val book = documentSnapshot.toObject(Book::class.java)?.copy(id = documentSnapshot.id)
                    if (book != null) {
                        Log.d(TAG, "Book ID $bookId fetched: ${book.title}")
                        trySend(Resource.Success(book))
                    } else {
                        Log.w(TAG, "Book with ID $bookId exists but data conversion failed.")
                        trySend(Resource.Success(null))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting document to Book for ID $bookId", e)
                    trySend(Resource.Error("Erreur de conversion des données du livre: ${e.localizedMessage}"))
                }
            } else {
                Log.w(TAG, "Book with ID $bookId does not exist.")
                trySend(Resource.Success(null))
            }
        }
        awaitClose {
            Log.d(TAG, "Closing listener for book ID $bookId.")
            listenerRegistration.remove()
        }
    }

    override suspend fun addBook(book: Book): Resource<String> {
        return try {
            // Note : les champs non définis dans le hashMap ne seront pas ajoutés
            val bookData = hashMapOf(
                "title" to book.title,
                "author" to book.author,
                "synopsis" to book.synopsis,
                "coverImageUrl" to book.coverImageUrl,
                "totalPages" to book.totalPages,
                "contentUrl" to book.contentUrl,
                "publisher" to book.publisher,
                "language" to book.language,
                "publicationDate" to book.publicationDate,
                "isbn" to book.isbn,
                "genre" to book.genre
            )

            val documentRef = booksCollection
                .add(bookData)
                .await()
            Log.d(TAG, "Book added successfully with ID: ${documentRef.id}")
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
            // Utilisation directe de l'objet book pour la mise à jour, Firestore s'occupe de la sérialisation
            booksCollection.document(book.id)
                .set(book) // set() est plus sûr pour un objet complet
                .await()
            Log.d(TAG, "Book ID ${book.id} updated successfully: ${book.title}")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating book ${book.id}: $e", e)
            Resource.Error("Erreur lors de la mise à jour du livre: ${e.localizedMessage}")
        }
    }

    // --- IMPLÉMENTATION DES NOUVELLES FONCTIONS ---

    override suspend fun addBookToUserLibrary(userId: String, book: Book): Resource<Unit> {
        if (userId.isBlank() || book.id.isBlank()) {
            val errorMsg = "User ID and Book ID cannot be blank."
            Log.e(TAG, "addBookToUserLibrary failed: $errorMsg")
            return Resource.Error(errorMsg)
        }

        return try {
            val libraryEntry = UserLibraryEntry(
                bookId = book.id,
                userId = userId,
                status = ReadingStatus.TO_READ,
                currentPage = 0,
                totalPages = book.totalPages,
                addedDate = null, // Sera rempli par le @ServerTimestamp
                lastReadDate = null
            )

            usersCollection.document(userId)
                .collection(COLLECTION_LIBRARY)
                .document(book.id)
                .set(libraryEntry)
                .await()

            Log.d(TAG, "Book '${book.title}' (ID: ${book.id}) added to library for user $userId")
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
                Log.w(TAG, "Error listening for book $bookId in user $userId library", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            // `snapshot.exists()` est la manière la plus directe de vérifier la présence.
            val inLibrary = snapshot != null && snapshot.exists()
            Log.d(TAG, "Book $bookId in library for user $userId: $inLibrary")
            trySend(Resource.Success(inLibrary))
        }

        awaitClose {
            Log.d(TAG, "Closing listener for book $bookId in user $userId library.")
            listener.remove()
        }
    }
}