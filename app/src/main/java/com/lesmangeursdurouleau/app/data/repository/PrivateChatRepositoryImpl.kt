// PRÊT À COLLER - Fichier complet
package com.lesmangeursdurouleau.app.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.model.MessageStatus
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class PrivateChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorageService: FirebaseStorageService,
    private val functions: FirebaseFunctions
) : PrivateChatRepository {

    companion object {
        private const val TAG = "PrivateChatRepository"
        private const val PERSISTENCE_TAG = "FirestorePersistence"
    }

    init {
        val isPersistenceEnabled = try {
            firestore.firestoreSettings.isPersistenceEnabled
        } catch (e: Exception) {
            "Non configurable (défaut probable)"
        }
        Log.d(PERSISTENCE_TAG, "Instance de Firestore injectée. Persistance activée : $isPersistenceEnabled")
    }

    private val conversationsCollection = firestore.collection(FirebaseConstants.COLLECTION_CONVERSATIONS)
    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    override fun getConversation(conversationId: String): Flow<Resource<Conversation>> = callbackFlow {
        trySend(Resource.Loading())
        val docRef = conversationsCollection.document(conversationId)

        var hasSentData = false
        val listenerRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (snapshot != null && snapshot.exists()) {
                val conversation = snapshot.toObject(Conversation::class.java)
                if (conversation != null) {
                    hasSentData = true
                    trySend(Resource.Success(conversation))
                } else if (!hasSentData) {
                    trySend(Resource.Error("Impossible de lire les données de la conversation."))
                }
            } else if (error != null) {
                if (!hasSentData) {
                    Log.w(TAG, "getConversation: Erreur SANS données cache pour $conversationId: ${error.message}")
                    trySend(Resource.Error("Erreur de connexion."))
                } else {
                    Log.d(TAG, "getConversation: Erreur réseau (ex: ${error.message}) mais données déjà servies du cache. On ignore.")
                }
            } else if (!hasSentData) {
                trySend(Resource.Error("La conversation n'existe pas."))
            }
        }
        awaitClose { listenerRegistration.remove() }
    }

    override fun getUserConversations(userId: String): Flow<Resource<List<Conversation>>> = callbackFlow {
        trySend(Resource.Loading())
        val query = conversationsCollection
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

        var hasSentData = false
        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (snapshot != null) {
                hasSentData = true
                val conversations = snapshot.toObjects(Conversation::class.java)
                trySend(Resource.Success(conversations))
            } else if (error != null) {
                if (!hasSentData) {
                    Log.w(TAG, "getUserConversations: Erreur SANS données cache pour $userId: ${error.message}")
                    trySend(Resource.Error("Erreur de connexion."))
                } else {
                    Log.d(TAG, "getUserConversations: Erreur réseau (ex: ${error.message}) mais données déjà servies du cache. On ignore.")
                }
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
                if (snapshot.exists()) { return@runTransaction }
                val currentUserDoc = transaction.get(usersCollection.document(currentUserId))
                val targetUserDoc = transaction.get(usersCollection.document(targetUserId))
                val newConversation = Conversation(
                    participantIds = participants,
                    participantNames = mapOf(
                        currentUserId to (currentUserDoc.getString("username") ?: ""),
                        targetUserId to (targetUserDoc.getString("username") ?: "")
                    ),
                    participantPhotoUrls = mapOf(
                        currentUserId to (currentUserDoc.getString("profilePictureUrl") ?: ""),
                        targetUserId to (targetUserDoc.getString("profilePictureUrl") ?: "")
                    ),
                    unreadCount = mapOf(currentUserId to 0, targetUserId to 0)
                )
                transaction.set(conversationDocRef, newConversation)
            }.await()
            Resource.Success(conversationId)
        } catch (e: Exception) {
            Log.e(TAG, "createOrGetConversation: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors du démarrage de la conversation.")
        }
    }

    override fun getConversationMessages(conversationId: String): Flow<Resource<List<PrivateMessage>>> = callbackFlow {
        trySend(Resource.Loading())
        val query = conversationsCollection.document(conversationId)
            .collection(FirebaseConstants.SUBCOLLECTION_MESSAGES)
            .orderBy("timestamp", Query.Direction.ASCENDING)

        var hasSentData = false
        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (snapshot != null) {
                hasSentData = true
                val messages = snapshot.documents.mapNotNull { it.toObject(PrivateMessage::class.java)?.copy(id = it.id) }
                trySend(Resource.Success(messages))
            } else if (error != null) {
                if (!hasSentData) {
                    Log.w(TAG, "getConversationMessages: Erreur SANS données cache pour $conversationId: ${error.message}")
                    trySend(Resource.Error("Erreur de connexion."))
                } else {
                    Log.d(TAG, "getConversationMessages: Erreur réseau (ex: ${error.message}) mais données déjà servies du cache. On ignore.")
                }
            }
        }
        awaitClose { listenerRegistration.remove() }
    }

    override suspend fun sendPrivateMessage(conversationId: String, message: PrivateMessage): Resource<Unit> {
        return try {
            conversationsCollection.document(conversationId).collection(FirebaseConstants.SUBCOLLECTION_MESSAGES).add(message).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendPrivateMessage: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de l'envoi du message.")
        }
    }

    override suspend fun sendImageMessage(conversationId: String, imageUri: Uri, text: String?): Resource<Unit> {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return Resource.Error("Utilisateur non authentifié.")
        return try {
            val fileName = "${UUID.randomUUID()}.jpg"
            val uploadResult = firebaseStorageService.uploadChatMessageImage(conversationId, fileName, imageUri)
            val imageUrl = (uploadResult as? Resource.Success)?.data ?: return Resource.Error((uploadResult as? Resource.Error)?.message ?: "Erreur d'upload de l'image.")
            val message = PrivateMessage(senderId = currentUserId, text = text, imageUrl = imageUrl)
            sendPrivateMessage(conversationId, message)
        } catch (e: Exception) {
            Log.e(TAG, "sendImageMessage: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de l'envoi de l'image.")
        }
    }

    override suspend fun deletePrivateMessage(conversationId: String, messageId: String): Resource<Unit> {
        return try {
            val messagesRef = conversationsCollection.document(conversationId).collection(FirebaseConstants.SUBCOLLECTION_MESSAGES)
            messagesRef.document(messageId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deletePrivateMessage: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de la suppression du message.")
        }
    }

    override suspend fun markConversationAsRead(conversationId: String): Resource<Unit> {
        if (firebaseAuth.currentUser == null) return Resource.Error("Utilisateur non authentifié.")
        return try {
            functions.getHttpsCallable("markConversationAsRead").call(hashMapOf("conversationId" to conversationId)).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "markConversationAsRead: Échec de l'appel à la Cloud Function: ${e.message}", e)
            Resource.Error("Erreur lors de la mise à jour du statut de lecture.")
        }
    }

    override suspend fun addOrUpdateReaction(conversationId: String, messageId: String, userId: String, emoji: String): Resource<Unit> {
        return try {
            val messageRef = conversationsCollection.document(conversationId).collection(FirebaseConstants.SUBCOLLECTION_MESSAGES).document(messageId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(messageRef)
                val reactions = snapshot.get("reactions") as? MutableMap<String, String> ?: mutableMapOf()
                if (reactions[userId] == emoji) reactions.remove(userId) else reactions[userId] = emoji
                transaction.update(messageRef, "reactions", reactions)
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addOrUpdateReaction: Erreur: ${e.message}", e)
            Resource.Error("Erreur de réaction.")
        }
    }

    override suspend fun editPrivateMessage(conversationId: String, messageId: String, newText: String): Resource<Unit> {
        return try {
            val messageRef = conversationsCollection.document(conversationId).collection(FirebaseConstants.SUBCOLLECTION_MESSAGES).document(messageId)
            messageRef.update("text", newText, "isEdited", true).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "editPrivateMessage: Erreur: ${e.message}", e)
            Resource.Error("Erreur de modification.")
        }
    }

    override suspend fun updateFavoriteStatus(conversationId: String, isFavorite: Boolean): Resource<Unit> {
        return try {
            conversationsCollection.document(conversationId).update("isFavorite", isFavorite).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateFavoriteStatus: Erreur: ${e.message}", e)
            Resource.Error("Erreur de mise à jour du favori.")
        }
    }

    override suspend fun updateTypingStatus(conversationId: String, userId: String, isTyping: Boolean): Resource<Unit> {
        return try {
            conversationsCollection.document(conversationId).update("typingStatus.$userId", isTyping).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateTypingStatus: Erreur: ${e.message}", e)
            Resource.Error("Erreur de mise à jour du statut de saisie.")
        }
    }

    override suspend fun updateUserActiveStatus(conversationId: String, userId: String, isActive: Boolean): Resource<Unit> {
        return try {
            val updateValue = if (isActive) FieldValue.arrayUnion(userId) else FieldValue.arrayRemove(userId)
            conversationsCollection.document(conversationId).update("activeParticipantIds", updateValue).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserActiveStatus: Erreur: ${e.message}", e)
            Resource.Error("Erreur de mise à jour de la présence.")
        }
    }

    // === DÉBUT DE LA MODIFICATION ===
    override suspend fun completeChallenge(conversationId: String, challengeId: String, bonusPoints: Int): Resource<Unit> {
        if (conversationId.isBlank() || challengeId.isBlank() || bonusPoints <= 0) {
            return Resource.Error("Données du défi invalides.")
        }
        return try {
            val conversationRef = conversationsCollection.document(conversationId)
            conversationRef.update(
                "completedChallengeIds", FieldValue.arrayUnion(challengeId),
                "affinityScore", FieldValue.increment(bonusPoints.toLong())
            ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "completeChallenge: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de la validation du défi.")
        }
    }
    // === FIN DE LA MODIFICATION ===
}