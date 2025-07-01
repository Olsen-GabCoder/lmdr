// PRÊT À COLLER - Fichier complet et corrigé
package com.lesmangeursdurouleau.app.data.repository

import android.net.Uri
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
    }

    private val conversationsCollection = firestore.collection(FirebaseConstants.COLLECTION_CONVERSATIONS)
    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    // CORRIGÉ: Structure de callbackFlow simplifiée et robuste.
    override fun getConversation(conversationId: String): Flow<Resource<Conversation>> = callbackFlow {
        trySend(Resource.Loading())
        val listenerRegistration = conversationsCollection.document(conversationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "getConversation: Erreur pour $conversationId: ${error.message}", error)
                    trySend(Resource.Error("Erreur de connexion: ${error.localizedMessage}"))
                    close(error) // Ferme le flow en cas d'erreur
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

    // CORRIGÉ: Structure de callbackFlow simplifiée et robuste.
    override fun getUserConversations(userId: String): Flow<Resource<List<Conversation>>> = callbackFlow {
        trySend(Resource.Loading())
        val query = conversationsCollection
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "getUserConversations: Erreur pour $userId: ${error.message}", error)
                trySend(Resource.Error("Erreur de connexion: ${error.localizedMessage}"))
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

    // CORRIGÉ: Logique de transaction simplifiée. La récupération des détails des participants
    // sera la responsabilité du ViewModel pour toujours avoir des données à jour.
    override suspend fun createOrGetConversation(currentUserId: String, targetUserId: String): Resource<String> {
        return try {
            val participants = listOf(currentUserId, targetUserId).sorted()
            val conversationId = "${participants[0]}_${participants[1]}"
            val conversationDocRef = conversationsCollection.document(conversationId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(conversationDocRef)
                if (snapshot.exists()) {
                    return@runTransaction // La conversation existe déjà, on ne fait rien.
                }

                // NOTE STRATÉGIQUE: On ne stocke plus les noms/photos ici pour éviter la désynchronisation.
                // Le ViewModel sera chargé de récupérer les profils à jour des participants via leurs ID.
                val newConversation = Conversation(
                    id = conversationId,
                    participantIds = participants,
                    unreadCount = mapOf(currentUserId to 0, targetUserId to 0)
                )
                transaction.set(conversationDocRef, newConversation)
            }.await()
            Resource.Success(conversationId)
        } catch (e: Exception) {
            Log.e(TAG, "createOrGetConversation: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors du démarrage de la conversation: ${e.localizedMessage}")
        }
    }

    // CORRIGÉ: Structure de callbackFlow simplifiée et robuste.
    override fun getConversationMessages(conversationId: String): Flow<Resource<List<PrivateMessage>>> = callbackFlow {
        trySend(Resource.Loading())
        val query = conversationsCollection.document(conversationId)
            .collection(FirebaseConstants.SUBCOLLECTION_MESSAGES)
            .orderBy("timestamp", Query.Direction.ASCENDING)

        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "getConversationMessages: Erreur pour $conversationId: ${error.message}", error)
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

    // CORRIGÉ: Logique simplifiée. La création de l'objet message sera gérée en amont (UseCase/ViewModel).
    override suspend fun sendPrivateMessage(conversationId: String, message: PrivateMessage): Resource<Unit> {
        return try {
            conversationsCollection.document(conversationId)
                .collection(FirebaseConstants.SUBCOLLECTION_MESSAGES)
                .add(message)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendPrivateMessage: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de l'envoi du message: ${e.localizedMessage}")
        }
    }

    // CORRIGÉ: Utilisation d'un bloc `runCatching` pour une gestion d'erreur plus idiomatique en Kotlin.
    override suspend fun sendImageMessage(conversationId: String, imageUri: Uri, text: String?): Resource<Unit> {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return Resource.Error("Utilisateur non authentifié.")

        return runCatching {
            val fileName = "${UUID.randomUUID()}.jpg"
            val uploadResult = firebaseStorageService.uploadChatMessageImage(conversationId, fileName, imageUri)

            val imageUrl = when (uploadResult) {
                is Resource.Success -> uploadResult.data
                is Resource.Error -> throw Exception(uploadResult.message ?: "Erreur d'upload de l'image.")
                is Resource.Loading -> throw IllegalStateException("L'upload ne peut être en chargement ici.")
            }

            val message = PrivateMessage(senderId = currentUserId, text = text, imageUrl = imageUrl)
            sendPrivateMessage(conversationId, message)
        }.fold(
            onSuccess = { it }, // Retourne le résultat de sendPrivateMessage
            onFailure = {
                Log.e(TAG, "sendImageMessage: Erreur: ${it.message}", it)
                Resource.Error("Erreur lors de l'envoi de l'image: ${it.localizedMessage}")
            }
        )
    }

    override suspend fun deletePrivateMessage(conversationId: String, messageId: String): Resource<Unit> {
        return try {
            conversationsCollection.document(conversationId)
                .collection(FirebaseConstants.SUBCOLLECTION_MESSAGES)
                .document(messageId)
                .delete()
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deletePrivateMessage: Erreur: ${e.message}", e)
            Resource.Error("Erreur de suppression: ${e.localizedMessage}")
        }
    }

    override suspend fun markConversationAsRead(conversationId: String): Resource<Unit> {
        if (firebaseAuth.currentUser == null) return Resource.Error("Utilisateur non authentifié.")
        return try {
            functions.getHttpsCallable("markConversationAsRead")
                .call(hashMapOf("conversationId" to conversationId))
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "markConversationAsRead: Échec de l'appel à la Cloud Function: ${e.message}", e)
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
                    reactions.remove(userId) // Si l'utilisateur clique sur la même réaction, on la retire.
                } else {
                    reactions[userId] = emoji // Sinon, on l'ajoute ou la met à jour.
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
            messageRef.update(mapOf(
                "text" to newText,
                "isEdited" to true
            )).await()
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
            // Utilisation de la notation par points pour les champs de map
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

    // CORRIGÉ: Validation déplacée en amont, le repository fait confiance aux données reçues.
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
}