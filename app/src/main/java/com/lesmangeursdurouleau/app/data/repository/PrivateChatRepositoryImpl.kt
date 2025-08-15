// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PrivateChatRepositoryImpl.kt
package com.lesmangeursdurouleau.app.data.repository

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.ui.members.ConversationFilterType
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject

class PrivateChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorageService: FirebaseStorageService,
    private val functions: FirebaseFunctions
) : PrivateChatRepository {

    companion object {
        private const val TAG = "PrivateChatRepository"
    }

    private val conversationsCollection = firestore.collection(FirebaseConstants.COLLECTION_CONVERSATIONS)
    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    override fun getConversation(conversationId: String): Flow<Resource<Conversation>> = callbackFlow {
        trySend(Resource.Loading())
        val listenerRegistration = conversationsCollection.document(conversationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "getConversation: Erreur pour $conversationId", error)
                    trySend(Resource.Error("Erreur de connexion: ${error.localizedMessage}"))
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    snapshot.toObject(Conversation::class.java)?.let {
                        trySend(Resource.Success(it))
                    } ?: trySend(Resource.Error("Impossible de lire les données de la conversation."))
                } else {
                    trySend(Resource.Error("La conversation n'existe pas."))
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    @Deprecated("Non-performant.")
    override fun getUserConversations(userId: String): Flow<Resource<List<Conversation>>> {
        return getFilteredUserConversations(userId, ConversationFilterType.ALL)
    }

    override fun getFilteredUserConversations(userId: String, filterType: String): Flow<Resource<List<Conversation>>> = callbackFlow {
        trySend(Resource.Loading())

        // === DÉBUT DE LA CORRECTION ===
        // La requête est volontairement simplifiée pour contourner les limitations de Firestore.
        // Le filtrage complexe (Non lues, Favoris) et le tri (Épinglées) sont désormais gérés côté client dans le ViewModel.
        var query: Query = conversationsCollection.whereArrayContains("participantIds", userId)

        // Le seul filtre conservé côté serveur est celui sur l'état "archivé",
        // car il s'agit d'un ensemble de données distinct que l'on ne veut généralement pas charger avec le reste.
        if (filterType == ConversationFilterType.ARCHIVED) {
            query = query.whereEqualTo("isArchived", true)
        } else {
            // Pour tous les autres filtres (ALL, UNREAD, etc.), on charge les conversations non archivées.
            query = query.whereEqualTo("isArchived", false)
        }

        // Le tri est également simplifié au maximum. Le tri par 'isPinned' est maintenant géré côté client.
        // On conserve un tri par date pour que les données arrivent dans un ordre logique.
        val finalQuery = query.orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
        // === FIN DE LA CORRECTION ===

        val listenerRegistration = finalQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Erreur getFilteredUserConversations (filter: $filterType): ${error.message}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val conversations = snapshot.toObjects(Conversation::class.java)
                trySend(Resource.Success(conversations))
            }
        }
        awaitClose { listenerRegistration.remove() }
    }


    override suspend fun createOrGetConversation(currentUserId: String, targetUserId: String): Resource<String> {
        return try {
            val participants = listOf(currentUserId, targetUserId).sorted()
            val conversationId = "${participants[0]}_${participants[1]}"
            val conversationDocRef = conversationsCollection.document(conversationId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(conversationDocRef)
                if (snapshot.exists()) {
                    return@runTransaction
                }

                val currentUserDoc = transaction.get(usersCollection.document(currentUserId))
                val targetUserDoc = transaction.get(usersCollection.document(targetUserId))

                val currentUsername = currentUserDoc.getString("username") ?: "Utilisateur"
                val targetUsername = targetUserDoc.getString("username") ?: "Utilisateur"
                val currentUserPhoto = currentUserDoc.getString("profilePictureUrl")
                val targetUserPhoto = targetUserDoc.getString("profilePictureUrl")

                val newConversation = Conversation(
                    id = conversationId,
                    participantIds = participants,
                    participantNames = mapOf(currentUserId to currentUsername, targetUserId to targetUsername),
                    participantPhotoUrls = mapOf(
                        currentUserId to (currentUserPhoto ?: ""),
                        targetUserId to (targetUserPhoto ?: "")
                    )
                )
                transaction.set(conversationDocRef, newConversation)
            }.await()
            Resource.Success(conversationId)
        } catch (e: Exception) {
            Log.e(TAG, "createOrGetConversation Erreur: ${e.message}", e)
            Resource.Error("Erreur lors du démarrage de la conversation: ${e.localizedMessage}")
        }
    }

    override fun getConversationMessages(conversationId: String): Flow<Resource<List<PrivateMessage>>> = callbackFlow {
        trySend(Resource.Loading())
        val query = conversationsCollection.document(conversationId)
            .collection(FirebaseConstants.SUBCOLLECTION_MESSAGES)
            .orderBy("timestamp", Query.Direction.ASCENDING)

        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "getConversationMessages: Erreur pour $conversationId", error)
                trySend(Resource.Error("Erreur de connexion: ${error.localizedMessage}"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val messages = snapshot.documents.mapNotNull { it.toObject(PrivateMessage::class.java)?.copy(id = it.id) }
                trySend(Resource.Success(messages))
            }
        }
        awaitClose { listenerRegistration.remove() }
    }

    @Deprecated("Obsolète")
    override fun getConversationMessagesAfter(conversationId: String, afterTimestamp: Date?): Flow<Resource<List<PrivateMessage>>> {
        return flowOf(Resource.Error("Cette méthode est dépréciée."))
    }

    @Deprecated("Obsolète")
    override suspend fun getConversationMessagesPaginated(
        conversationId: String,
        lastVisibleMessageId: String?,
        pageSize: Int
    ): Resource<PaginatedMessagesResponse> {
        return Resource.Error("Cette méthode est dépréciée.")
    }

    override suspend fun sendPrivateMessage(conversationId: String, message: PrivateMessage): Resource<Unit> {
        return try {
            conversationsCollection.document(conversationId).collection(FirebaseConstants.SUBCOLLECTION_MESSAGES).add(message).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendPrivateMessage: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de l'envoi du message: ${e.localizedMessage}")
        }
    }

    override suspend fun sendImageMessage(conversationId: String, imageData: ByteArray, text: String?): Resource<Unit> {
        if (firebaseAuth.currentUser == null) {
            return Resource.Error("Utilisateur non authentifié.")
        }
        return try {
            val imageBase64 = Base64.encodeToString(imageData, Base64.DEFAULT)
            val data = hashMapOf(
                "conversationId" to conversationId,
                "imageBase64" to imageBase64,
                "text" to text
            )
            functions.getHttpsCallable("sendChatMessageImage").call(data).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendImageMessage: Échec de l'appel à la Cloud Function", e)
            Resource.Error("Erreur lors de l'envoi de l'image: ${e.localizedMessage}")
        }
    }

    override suspend fun deletePrivateMessage(conversationId: String, messageId: String): Resource<Unit> {
        return try {
            conversationsCollection.document(conversationId).collection(FirebaseConstants.SUBCOLLECTION_MESSAGES).document(messageId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deletePrivateMessage: Erreur: ${e.message}", e)
            Resource.Error("Erreur de suppression: ${e.localizedMessage}")
        }
    }

    override suspend fun markConversationAsRead(conversationId: String): Resource<Unit> {
        if (firebaseAuth.currentUser == null) return Resource.Error("Utilisateur non authentifié.")
        return try {
            functions.getHttpsCallable("markConversationAsRead").call(hashMapOf("conversationId" to conversationId)).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "markConversationAsRead: Échec de l'appel à la Cloud Function", e)
            Resource.Error("Erreur de mise à jour: ${e.localizedMessage}")
        }
    }

    override suspend fun addOrUpdateReaction(conversationId: String, messageId: String, userId: String, emoji: String): Resource<Unit> {
        return try {
            val messageRef = conversationsCollection.document(conversationId).collection(FirebaseConstants.SUBCOLLECTION_MESSAGES).document(messageId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(messageRef)
                val reactions = (snapshot.get("reactions") as? Map<String, String> ?: emptyMap()).toMutableMap()
                if (reactions[userId] == emoji) {
                    reactions.remove(userId)
                } else {
                    reactions[userId] = emoji
                }
                transaction.update(messageRef, "reactions", reactions)
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addOrUpdateReaction: Erreur: ${e.message}", e)
            Resource.Error("Erreur de réaction: ${e.localizedMessage}")
        }
    }

    override suspend fun editPrivateMessage(conversationId: String, messageId: String, newText: String): Resource<Unit> {
        return try {
            val messageRef = conversationsCollection.document(conversationId).collection(FirebaseConstants.SUBCOLLECTION_MESSAGES).document(messageId)
            messageRef.update(mapOf("text" to newText, "isEdited" to true)).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "editPrivateMessage: Erreur: ${e.message}", e)
            Resource.Error("Erreur de modification: ${e.localizedMessage}")
        }
    }

    override suspend fun updateFavoriteStatus(conversationId: String, isFavorite: Boolean): Resource<Unit> {
        return try {
            conversationsCollection.document(conversationId).update("isFavorite", isFavorite).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateFavoriteStatus: Erreur: ${e.message}", e)
            Resource.Error("Erreur de mise à jour du favori: ${e.localizedMessage}")
        }
    }

    override suspend fun updateTypingStatus(conversationId: String, userId: String, isTyping: Boolean): Resource<Unit> {
        return try {
            conversationsCollection.document(conversationId).update("typingStatus.$userId", isTyping).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateTypingStatus: Erreur: ${e.message}", e)
            Resource.Error("Erreur de mise à jour du statut: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserActiveStatus(conversationId: String, userId: String, isActive: Boolean): Resource<Unit> {
        return try {
            val updateValue = if (isActive) FieldValue.arrayUnion(userId) else FieldValue.arrayRemove(userId)
            conversationsCollection.document(conversationId).update("activeParticipantIds", updateValue).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserActiveStatus: Erreur: ${e.message}", e)
            Resource.Error("Erreur de mise à jour de la présence: ${e.localizedMessage}")
        }
    }

    override suspend fun completeChallenge(conversationId: String, challengeId: String, bonusPoints: Int): Resource<Unit> {
        return try {
            conversationsCollection.document(conversationId).update(mapOf(
                "completedChallengeIds" to FieldValue.arrayUnion(challengeId),
                "affinityScore" to FieldValue.increment(bonusPoints.toLong())
            )).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "completeChallenge: Erreur: ${e.message}", e)
            Resource.Error("Erreur de validation du défi: ${e.localizedMessage}")
        }
    }

    private suspend fun updateConversationFields(conversationIds: List<String>, field: String, value: Any): Resource<Unit> {
        if (conversationIds.isEmpty()) return Resource.Success(Unit)
        return try {
            firestore.runBatch { batch ->
                conversationIds.forEach { id ->
                    val docRef = conversationsCollection.document(id)
                    batch.update(docRef, field, value)
                }
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la mise à jour du champ '$field'", e)
            Resource.Error("Erreur lors de la mise à jour : ${e.localizedMessage}")
        }
    }

    override suspend fun updatePinnedStatus(conversationIds: List<String>, isPinned: Boolean): Resource<Unit> {
        return updateConversationFields(conversationIds, "isPinned", isPinned)
    }

    override suspend fun updateArchivedStatus(conversationIds: List<String>, isArchived: Boolean): Resource<Unit> {
        return updateConversationFields(conversationIds, "isArchived", isArchived)
    }

    override suspend fun deleteConversations(conversationIds: List<String>): Resource<Unit> {
        if (conversationIds.isEmpty()) return Resource.Success(Unit)
        return try {
            val data = hashMapOf("conversationIds" to conversationIds)
            functions.getHttpsCallable("deleteConversations")
                .call(data)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'appel à la Cloud Function deleteConversations", e)
            Resource.Error("Erreur de suppression : ${e.localizedMessage}")
        }
    }
}