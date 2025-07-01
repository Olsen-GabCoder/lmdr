// PRÊT À COLLER - Fichier 100% complet et corrigé
package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SocialRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : SocialRepository {

    companion object {
        private const val TAG = "SocialRepositoryImpl"
    }

    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)
    private val booksCollection = firestore.collection(FirebaseConstants.COLLECTION_BOOKS)

    override suspend fun followUser(currentUserId: String, targetUserId: String): Resource<Unit> {
        return try {
            if (currentUserId == targetUserId) return Resource.Error("Vous ne pouvez pas vous suivre vous-même.")
            firestore.runTransaction { transaction ->
                val currentUserRef = usersCollection.document(currentUserId)
                val targetUserRef = usersCollection.document(targetUserId)
                if (!transaction.get(targetUserRef).exists()) {
                    throw FirebaseFirestoreException("L'utilisateur à suivre n'existe pas.", FirebaseFirestoreException.Code.NOT_FOUND)
                }
                transaction.set(currentUserRef.collection("following").document(targetUserId), mapOf("timestamp" to FieldValue.serverTimestamp()))
                transaction.set(targetUserRef.collection("followers").document(currentUserId), mapOf("timestamp" to FieldValue.serverTimestamp()))
                transaction.update(currentUserRef, "followingCount", FieldValue.increment(1))
                transaction.update(targetUserRef, "followersCount", FieldValue.increment(1))
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "followUser: Erreur: ${e.message}", e)
            Resource.Error(e.localizedMessage ?: "Une erreur est survenue lors du suivi.")
        }
    }

    override suspend fun unfollowUser(currentUserId: String, targetUserId: String): Resource<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val currentUserRef = usersCollection.document(currentUserId)
                val targetUserRef = usersCollection.document(targetUserId)
                transaction.delete(currentUserRef.collection("following").document(targetUserId))
                transaction.delete(targetUserRef.collection("followers").document(currentUserId))
                transaction.update(currentUserRef, "followingCount", FieldValue.increment(-1))
                transaction.update(targetUserRef, "followersCount", FieldValue.increment(-1))
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "unfollowUser: Erreur: ${e.message}", e)
            Resource.Error(e.localizedMessage ?: "Une erreur est survenue lors du désabonnement.")
        }
    }

    override fun isFollowing(currentUserId: String, targetUserId: String): Flow<Resource<Boolean>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = usersCollection.document(currentUserId).collection("following").document(targetUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                    close(error)
                    return@addSnapshotListener
                }
                trySend(Resource.Success(snapshot?.exists() == true))
            }
        awaitClose { listener.remove() }
    }

    private fun getSocialList(userId: String, subCollection: String): Flow<Resource<List<User>>> = callbackFlow {
        trySend(Resource.Loading())
        val collectionRef = usersCollection.document(userId).collection(subCollection)
        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                close(error)
                return@addSnapshotListener
            }
            val userIds = snapshot?.documents?.map { it.id } ?: emptyList()
            if (userIds.isEmpty()) {
                trySend(Resource.Success(emptyList()))
            } else {
                usersCollection.whereIn(FieldPath.documentId(), userIds).get()
                    .addOnSuccessListener { usersSnapshot ->
                        trySend(Resource.Success(usersSnapshot.toObjects(User::class.java)))
                    }
                    .addOnFailureListener { e ->
                        trySend(Resource.Error(e.localizedMessage ?: "Erreur de chargement des profils."))
                    }
            }
        }
        awaitClose { listener.remove() }
    }

    override fun getFollowingUsers(userId: String): Flow<Resource<List<User>>> = getSocialList(userId, "following")
    override fun getFollowersUsers(userId: String): Flow<Resource<List<User>>> = getSocialList(userId, "followers")

    override suspend fun addCommentOnBook(bookId: String, comment: Comment): Resource<Unit> {
        return try {
            booksCollection.document(bookId).collection(FirebaseConstants.SUBCOLLECTION_COMMENTS).add(comment).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addCommentOnBook: Erreur: ${e.message}", e)
            Resource.Error("Erreur d'ajout du commentaire: ${e.localizedMessage}")
        }
    }

    override fun getCommentsForBook(bookId: String): Flow<Resource<List<Comment>>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = booksCollection.document(bookId).collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                    close(error); return@addSnapshotListener
                }
                if (snapshot != null) {
                    val comments = snapshot.documents.mapNotNull { it.toObject(Comment::class.java)?.copy(commentId = it.id) }
                    trySend(Resource.Success(comments))
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun deleteCommentOnBook(bookId: String, commentId: String): Resource<Unit> {
        return try {
            booksCollection.document(bookId).collection(FirebaseConstants.SUBCOLLECTION_COMMENTS).document(commentId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteCommentOnBook: Erreur: ${e.message}", e)
            Resource.Error("Erreur de suppression: ${e.localizedMessage}")
        }
    }

    override suspend fun toggleLikeOnBook(bookId: String, currentUserId: String): Resource<Unit> {
        return try {
            val likeDocRef = booksCollection.document(bookId).collection(FirebaseConstants.SUBCOLLECTION_LIKES).document(currentUserId)
            firestore.runTransaction { transaction ->
                if (transaction.get(likeDocRef).exists()) {
                    transaction.delete(likeDocRef)
                } else {
                    transaction.set(likeDocRef, mapOf("userId" to currentUserId, "timestamp" to FieldValue.serverTimestamp()))
                }
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "toggleLikeOnBook: Erreur: ${e.message}", e)
            Resource.Error("Erreur de like: ${e.localizedMessage}")
        }
    }

    override fun isBookLikedByUser(bookId: String, currentUserId: String): Flow<Resource<Boolean>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = booksCollection.document(bookId).collection(FirebaseConstants.SUBCOLLECTION_LIKES).document(currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                    close(error); return@addSnapshotListener
                }
                trySend(Resource.Success(snapshot?.exists() == true))
            }
        awaitClose { listener.remove() }
    }

    override fun getBookLikesCount(bookId: String): Flow<Resource<Int>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = booksCollection.document(bookId).collection(FirebaseConstants.SUBCOLLECTION_LIKES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                    close(error); return@addSnapshotListener
                }
                trySend(Resource.Success(snapshot?.size() ?: 0))
            }
        awaitClose { listener.remove() }
    }

    override suspend fun toggleLikeOnComment(bookId: String, commentId: String, currentUserId: String): Resource<Unit> {
        return try {
            val commentRef = booksCollection.document(bookId).collection(FirebaseConstants.SUBCOLLECTION_COMMENTS).document(commentId)
            val likeRef = commentRef.collection(FirebaseConstants.SUBCOLLECTION_LIKES).document(currentUserId)
            firestore.runTransaction { transaction ->
                if (transaction.get(likeRef).exists()) {
                    transaction.delete(likeRef)
                    transaction.update(commentRef, "likesCount", FieldValue.increment(-1))
                } else {
                    transaction.set(likeRef, mapOf("userId" to currentUserId, "timestamp" to FieldValue.serverTimestamp()))
                    transaction.update(commentRef, "likesCount", FieldValue.increment(1))
                }
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "toggleLikeOnComment: Erreur: ${e.message}", e)
            Resource.Error("Erreur de like sur commentaire: ${e.localizedMessage}")
        }
    }

    override fun isCommentLikedByCurrentUser(bookId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>> = callbackFlow {
        if (bookId.isBlank() || commentId.isBlank() || currentUserId.isBlank()) {
            trySend(Resource.Error("Informations manquantes pour vérifier le like du commentaire."))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        val listener = booksCollection.document(bookId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS).document(commentId)
            .collection(FirebaseConstants.SUBCOLLECTION_LIKES).document(currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                    close(error); return@addSnapshotListener
                }
                trySend(Resource.Success(snapshot?.exists() == true))
            }
        awaitClose { listener.remove() }
    }
}