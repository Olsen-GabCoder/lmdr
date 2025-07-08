// PRÊT À COLLER - Fichier NotificationRepositoryImpl.kt
package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.Notification
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : NotificationRepository {

    companion object {
        private const val TAG = "NotificationRepository"
        private const val SUBCOLLECTION_NOTIFICATIONS = "notifications"
    }

    override fun getNotifications(userId: String): Flow<Resource<List<Notification>>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(Resource.Error("ID utilisateur manquant."))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading())

        val notificationsCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)
            .document(userId)
            .collection(SUBCOLLECTION_NOTIFICATIONS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50) // Limiter à 50 pour éviter de charger un historique infini

        val listener = notificationsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Erreur lors de l'écoute des notifications pour $userId", error)
                trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val notifications = snapshot.documents.mapNotNull { document ->
                    try {
                        document.toObject(Notification::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur de désérialisation pour la notification ${document.id}", e)
                        null
                    }
                }
                trySend(Resource.Success(notifications))
            }
        }

        awaitClose { listener.remove() }
    }

    override suspend fun markNotificationAsRead(userId: String, notificationId: String): Resource<Unit> {
        if (userId.isBlank() || notificationId.isBlank()) {
            return Resource.Error("ID utilisateur ou ID notification manquant.")
        }
        return try {
            firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .update("read", true)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du marquage de la notification $notificationId comme lue", e)
            Resource.Error(e.localizedMessage ?: "Une erreur est survenue.")
        }
    }

    override suspend fun markAllNotificationsAsRead(userId: String): Resource<Unit> {
        if (userId.isBlank()) {
            return Resource.Error("ID utilisateur manquant.")
        }
        return try {
            val notificationsRef = firestore.collection(FirebaseConstants.COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_NOTIFICATIONS)

            val unreadNotificationsQuery = notificationsRef.whereEqualTo("read", false)
            val snapshot = unreadNotificationsQuery.get().await()

            if (snapshot.isEmpty) {
                return Resource.Success(Unit) // Aucune notification à marquer
            }

            val batch = firestore.batch()
            snapshot.documents.forEach { document ->
                batch.update(document.reference, "read", true)
            }
            batch.commit().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du marquage de toutes les notifications comme lues", e)
            Resource.Error(e.localizedMessage ?: "Une erreur est survenue.")
        }
    }
}