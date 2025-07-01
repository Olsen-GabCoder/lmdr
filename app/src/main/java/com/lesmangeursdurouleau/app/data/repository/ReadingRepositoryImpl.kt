// PRÊT À COLLER - Fichier complet, corrigé et allégé.
package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Repository dédié EXCLUSIVEMENT à la gestion des activités de lecture des utilisateurs
 * (lecture active et lectures terminées).
 * La logique sociale (commentaires, likes) a été déplacée dans SocialRepository.
 */
class ReadingRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ReadingRepository {

    companion object {
        private const val TAG = "ReadingRepositoryImpl"
    }

    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    override fun getCurrentReading(userId: String): Flow<Resource<UserBookReading?>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(Resource.Error("L'ID utilisateur ne peut pas être vide."))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        val docRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "getCurrentReading: Erreur pour $userId: ${error.message}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                snapshot.toObject(UserBookReading::class.java)?.let {
                    trySend(Resource.Success(it))
                } ?: trySend(Resource.Error("Erreur de conversion des données de lecture."))
            } else {
                // Si le document n'existe pas, il n'y a pas de lecture en cours. C'est un succès.
                trySend(Resource.Success(null))
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun updateCurrentReading(userId: String, userBookReading: UserBookReading?): Resource<Unit> {
        if (userId.isBlank()) return Resource.Error("L'ID utilisateur ne peut pas être vide.")
        val docRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
        return try {
            if (userBookReading != null) {
                docRef.set(userBookReading, SetOptions.merge()).await()
            } else {
                // Si userBookReading est null, on supprime le document de lecture active.
                docRef.delete().await()
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateCurrentReading: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de la mise à jour de la lecture: ${e.localizedMessage}")
        }
    }

    override suspend fun markActiveReadingAsCompleted(userId: String, activeReadingDetails: UserBookReading): Resource<Unit> {
        if (userId.isBlank() || activeReadingDetails.bookId.isBlank()) {
            return Resource.Error("Informations de lecture invalides.")
        }
        val activeReadingDocRef = usersCollection.document(userId).collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS).document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
        val completedReadingDocRef = usersCollection.document(userId).collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS).document(activeReadingDetails.bookId)
        val userDocRef = usersCollection.document(userId)

        return try {
            firestore.runTransaction { transaction ->
                // Création de l'objet CompletedReading normalisé, sans données dupliquées.
                val completedReading = CompletedReading(
                    bookId = activeReadingDetails.bookId,
                    userId = userId
                )

                transaction.set(completedReadingDocRef, completedReading)
                transaction.delete(activeReadingDocRef)
                transaction.update(userDocRef, "booksReadCount", FieldValue.increment(1))
                null
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "markActiveReadingAsCompleted: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de la finalisation de la lecture: ${e.localizedMessage}")
        }
    }

    override suspend fun removeCompletedReading(userId: String, bookId: String): Resource<Unit> {
        if (userId.isBlank() || bookId.isBlank()) return Resource.Error("Informations manquantes.")
        val docRef = usersCollection.document(userId).collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS).document(bookId)
        val userDocRef = usersCollection.document(userId)

        return try {
            firestore.runTransaction { transaction ->
                val readingSnapshot = transaction.get(docRef)
                if (!readingSnapshot.exists()) {
                    // Si la lecture n'existe pas, on considère que la suppression est un "succès" silencieux.
                    Log.w(TAG, "removeCompletedReading: Tentative de suppression d'une lecture non trouvée (bookId: $bookId).")
                    return@runTransaction
                }

                transaction.delete(docRef)
                transaction.update(userDocRef, "booksReadCount", FieldValue.increment(-1))
                null
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "removeCompletedReading: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de la suppression de la lecture: ${e.localizedMessage}")
        }
    }

    override fun getCompletedReadings(userId: String, orderBy: String, direction: Query.Direction): Flow<Resource<List<CompletedReading>>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(Resource.Error("ID utilisateur manquant."))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        val collectionRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .orderBy(orderBy, direction)

        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "getCompletedReadings: Erreur pour $userId: ${error.message}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                // Renvoie une liste d'objets CompletedReading allégés.
                val readings = snapshot.toObjects(CompletedReading::class.java)
                trySend(Resource.Success(readings))
            }
        }
        awaitClose { listener.remove() }
    }

    override fun getCompletedReadingDetail(userId: String, bookId: String): Flow<Resource<CompletedReading?>> = callbackFlow {
        if (userId.isBlank() || bookId.isBlank()) {
            trySend(Resource.Error("Informations manquantes."))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        val docRef = usersCollection.document(userId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMPLETED_READINGS)
            .document(bookId)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "getCompletedReadingDetail: Erreur pour $userId/$bookId: ${error.message}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                snapshot.toObject(CompletedReading::class.java)?.let {
                    trySend(Resource.Success(it))
                } ?: trySend(Resource.Error("Erreur de conversion."))
            } else {
                trySend(Resource.Success(null))
            }
        }
        awaitClose { listener.remove() }
    }
}