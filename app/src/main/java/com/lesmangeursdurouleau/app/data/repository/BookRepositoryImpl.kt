package com.lesmangeursdurouleau.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.ReadingStatus
import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.ui.members.SortDirection
import com.lesmangeursdurouleau.app.ui.members.SortOptions
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    // === DÉBUT DE L'AJOUT ===
    // NOUVEAU : Injection du contexte pour accéder aux SharedPreferences.
    // C'est une pratique acceptable dans la couche Repository.
    @ApplicationContext private val context: Context
    // === FIN DE L'AJOUT ===
) : BookRepository {

    companion object {
        private const val TAG = "BookRepositoryImpl"
        private const val SUBCOLLECTION_LIBRARY = "library"
        // NOUVEAU : Constante pour le nom du fichier de préférences.
        private const val PDF_READER_PREFS = "PdfReaderPrefs"
    }

    // === DÉBUT DE L'AJOUT ===
    // NOUVEAU : Instance des SharedPreferences, initialisée une seule fois.
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PDF_READER_PREFS, Context.MODE_PRIVATE)
    }
    // === FIN DE L'AJOUT ===

    private val booksCollection = firestore.collection(FirebaseConstants.COLLECTION_BOOKS)
    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    // --- Fonctions du catalogue de livres (INCHANGÉES) ---
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

    override fun getBooksByIds(bookIds: List<String>): Flow<Resource<List<Book>>> = flow {
        if (bookIds.isEmpty()) {
            emit(Resource.Success(emptyList()))
            return@flow
        }
        emit(Resource.Loading())

        try {
            val uniqueBookIds = bookIds.distinct()
            val bookIdChunks = uniqueBookIds.chunked(30)
            val books = mutableListOf<Book>()
            for (chunk in bookIdChunks) {
                val snapshot = booksCollection.whereIn(FieldPath.documentId(), chunk).get().await()
                val chunkBooks = snapshot.documents.mapNotNull { document ->
                    document.toObject(Book::class.java)?.apply { id = document.id }
                }
                books.addAll(chunkBooks)
            }
            emit(Resource.Success(books))
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la récupération des livres par IDs", e)
            emit(Resource.Error("Erreur lors du chargement des détails des livres: ${e.localizedMessage}"))
        }
    }

    override suspend fun addBook(book: Book): Resource<String> {
        return try {
            val bookToAdd = book.copy(id = "")
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
            booksCollection.document(book.id).set(book).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating book ${book.id}: $e", e)
            Resource.Error("Erreur lors de la mise à jour du livre: ${e.localizedMessage}")
        }
    }


    // --- Fonctions de la bibliothèque personnelle (INCHANGÉES) ---
    override fun getLibraryEntriesForUser(userId: String): Flow<Resource<List<UserLibraryEntry>>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(Resource.Error("L'ID de l'utilisateur ne peut être vide."))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        val query = usersCollection.document(userId).collection(SUBCOLLECTION_LIBRARY).orderBy("lastReadDate", Query.Direction.DESCENDING)
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val entries = snapshot.documents.mapNotNull { it.toObject(UserLibraryEntry::class.java) }
                trySend(Resource.Success(entries))
            }
        }
        awaitClose { listener.remove() }
    }

    override fun getCompletedLibraryEntriesForUser(userId: String, sortOptions: SortOptions): Flow<Resource<List<UserLibraryEntry>>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(Resource.Error("L'ID de l'utilisateur ne peut être vide."))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        val orderByField = when (sortOptions.orderBy) {
            "title" -> "bookTitle"
            "author" -> "bookAuthor"
            "lastReadDate" -> "lastReadDate"
            else -> {
                Log.w(TAG, "Option de tri '${sortOptions.orderBy}' non reconnue. Utilisation de 'lastReadDate'.")
                "lastReadDate"
            }
        }
        val firestoreDirection = if (sortOptions.direction == SortDirection.DESCENDING) Query.Direction.DESCENDING else Query.Direction.ASCENDING
        val query = usersCollection.document(userId).collection(SUBCOLLECTION_LIBRARY).whereEqualTo("status", ReadingStatus.FINISHED.name).orderBy(orderByField, firestoreDirection)
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Erreur Firestore dans getCompletedLibraryEntriesForUser", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                try {
                    val entries = snapshot.documents.mapNotNull { it.toObject(UserLibraryEntry::class.java) }
                    trySend(Resource.Success(entries))
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur de désérialisation dans getCompletedLibraryEntriesForUser", e)
                    trySend(Resource.Error("Erreur de conversion des données: ${e.localizedMessage}"))
                }
            }
        }
        awaitClose { listener.remove() }
    }

    override fun getLibraryEntry(userId: String, bookId: String): Flow<Resource<UserLibraryEntry?>> = callbackFlow {
        if (userId.isBlank() || bookId.isBlank()) {
            trySend(Resource.Error("L'ID de l'utilisateur et du livre ne peuvent être vides."))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        val docRef = usersCollection.document(userId).collection(SUBCOLLECTION_LIBRARY).document(bookId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                try {
                    val entry = snapshot.toObject(UserLibraryEntry::class.java)
                    trySend(Resource.Success(entry))
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur de désérialisation de UserLibraryEntry pour le livre ID $bookId", e)
                    trySend(Resource.Error("Erreur de conversion des données: ${e.localizedMessage}"))
                }
            } else {
                trySend(Resource.Success(null))
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun updateLibraryEntry(userId: String, entry: UserLibraryEntry): Resource<Unit> {
        if (userId.isBlank() || entry.bookId.isBlank()) {
            return Resource.Error("L'ID de l'utilisateur et du livre ne peuvent être vides.")
        }
        val userDocRef = usersCollection.document(userId)
        val libraryDocRef = userDocRef.collection(SUBCOLLECTION_LIBRARY).document(entry.bookId)
        return try {
            firestore.runTransaction { transaction ->
                val libraryEntrySnapshot = transaction.get(libraryDocRef)
                val oldStatus = if (libraryEntrySnapshot.exists()) {
                    libraryEntrySnapshot.toObject(UserLibraryEntry::class.java)?.status
                } else {
                    null
                }
                val newStatus = entry.status
                if (newStatus == ReadingStatus.FINISHED && oldStatus != ReadingStatus.FINISHED) {
                    transaction.update(userDocRef, "booksReadCount", FieldValue.increment(1))
                } else if (newStatus != ReadingStatus.FINISHED && oldStatus == ReadingStatus.FINISHED) {
                    transaction.update(userDocRef, "booksReadCount", FieldValue.increment(-1))
                }
                transaction.set(libraryDocRef, entry)
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la transaction de mise à jour pour l'entrée ${entry.bookId}", e)
            Resource.Error("Erreur de mise à jour transactionnelle: ${e.localizedMessage}")
        }
    }

    override suspend fun removeBookFromUserLibrary(userId: String, bookId: String): Resource<Unit> {
        if (userId.isBlank() || bookId.isBlank()) {
            return Resource.Error("L'ID de l'utilisateur et du livre sont requis.")
        }
        val userDocRef = usersCollection.document(userId)
        val libraryDocRef = userDocRef.collection(SUBCOLLECTION_LIBRARY).document(bookId)
        return try {
            firestore.runTransaction { transaction ->
                val libraryEntrySnapshot = transaction.get(libraryDocRef)
                if (!libraryEntrySnapshot.exists()) {
                    Log.w(TAG, "Tentative de suppression d'une entrée de bibliothèque non trouvée (bookId: $bookId).")
                    return@runTransaction
                }
                val libraryEntry = libraryEntrySnapshot.toObject(UserLibraryEntry::class.java)
                if (libraryEntry?.status == ReadingStatus.FINISHED) {
                    transaction.update(userDocRef, "booksReadCount", FieldValue.increment(-1))
                }
                transaction.delete(libraryDocRef)
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la suppression du livre $bookId de la bibliothèque de $userId", e)
            Resource.Error("Erreur lors de la suppression du livre : ${e.localizedMessage}")
        }
    }

    // === DÉBUT DE L'AJOUT ===
    // --- GESTION DE LA PROGRESSION DE LECTURE (LOCAL) ---

    private fun getProgressKey(userId: String, bookId: String) = "last_page_${userId}_$bookId"

    override suspend fun saveReadingProgress(userId: String, bookId: String, page: Int) {
        if (userId.isBlank() || bookId.isBlank()) return
        val key = getProgressKey(userId, bookId)
        sharedPreferences.edit().putInt(key, page).apply()
    }

    override suspend fun getReadingProgress(userId: String, bookId: String): Int {
        if (userId.isBlank() || bookId.isBlank()) return 0
        val key = getProgressKey(userId, bookId)
        return sharedPreferences.getInt(key, 0)
    }
    // === FIN DE L'AJOUT ===
}