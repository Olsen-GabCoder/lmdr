// PRÊT À COLLER - Fichier SocialRepositoryImpl.kt complet et MODIFIÉ
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
        private const val SUBCOLLECTION_FAVORITED_BY = "favoritedBy"
        private const val SUBCOLLECTION_RATINGS = "ratings"
        private const val SUBCOLLECTION_RECOMMENDED_BY = "recommendedBy"
        private const val SUBCOLLECTION_REPORTS = "reports"
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
                        val userList = usersSnapshot.documents.mapNotNull { doc -> doc.toObject(User::class.java)?.copy(uid = doc.id) }
                        trySend(Resource.Success(userList))
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
            val commentData = mutableMapOf<String, Any?>(
                "userId" to comment.userId,
                "userName" to comment.userName,
                "userPhotoUrl" to comment.userPhotoUrl,
                "targetUserId" to comment.targetUserId,
                "bookId" to comment.bookId,
                "commentText" to comment.commentText,
                "timestamp" to FieldValue.serverTimestamp()
            )
            if (comment.parentCommentId != null) {
                commentData["parentCommentId"] = comment.parentCommentId
            }

            booksCollection.document(bookId).collection(FirebaseConstants.SUBCOLLECTION_COMMENTS).add(commentData).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addCommentOnBook: Erreur: ${e.message}", e)
            Resource.Error("Erreur d'ajout du commentaire: ${e.localizedMessage}")
        }
    }

    override fun getCommentsForBook(bookId: String): Flow<Resource<List<Comment>>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = booksCollection.document(bookId).collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                    close(error); return@addSnapshotListener
                }
                if (snapshot != null) {
                    val comments = snapshot.documents.mapNotNull {
                        try {
                            it.toObject(Comment::class.java)?.copy(commentId = it.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Impossible de désérialiser le commentaire ${it.id}. Erreur: ${e.message}")
                            null
                        }
                    }.sortedByDescending { it.timestamp }
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

    override suspend fun updateCommentOnBook(bookId: String, commentId: String, newText: String): Resource<Unit> {
        if (bookId.isBlank() || commentId.isBlank() || newText.isBlank()) {
            return Resource.Error("Informations manquantes pour la mise à jour du commentaire.")
        }
        return try {
            val commentRef = booksCollection.document(bookId).collection(FirebaseConstants.SUBCOLLECTION_COMMENTS).document(commentId)
            val updates = mapOf(
                "commentText" to newText,
                "isEdited" to true,
                "lastEditTimestamp" to FieldValue.serverTimestamp()
            )
            commentRef.update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateCommentOnBook: Erreur: ${e.message}", e)
            Resource.Error("Erreur de mise à jour du commentaire: ${e.localizedMessage}")
        }
    }

    override suspend fun reportComment(bookId: String, commentId: String, reportingUserId: String, reason: String): Resource<Unit> {
        if (bookId.isBlank() || commentId.isBlank() || reportingUserId.isBlank()) {
            return Resource.Error("Informations manquantes pour le signalement.")
        }
        return try {
            val reportRef = booksCollection.document(bookId)
                .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS).document(commentId)
                .collection(SUBCOLLECTION_REPORTS).document(reportingUserId)

            val reportData = mapOf(
                "reportingUserId" to reportingUserId,
                "reason" to reason,
                "timestamp" to FieldValue.serverTimestamp()
            )
            reportRef.set(reportData).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "reportComment: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors du signalement: ${e.localizedMessage}")
        }
    }

    override suspend fun toggleLikeOnReading(targetUserId: String, bookId: String, likerId: String): Resource<Unit> {
        return try {
            val likeDocRef = usersCollection.document(targetUserId)
                .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
                .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
                .collection(FirebaseConstants.SUBCOLLECTION_ACTIVE_READING_LIKES)
                .document(likerId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(likeDocRef)
                if (snapshot.exists()) {
                    transaction.delete(likeDocRef)
                } else {
                    val likeData = mapOf(
                        "likerId" to likerId,
                        "bookId" to bookId,
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                    transaction.set(likeDocRef, likeData)
                }
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "toggleLikeOnReading: Erreur: ${e.message}", e)
            Resource.Error("Erreur de like: ${e.localizedMessage}")
        }
    }

    override fun isReadingLikedByUser(targetUserId: String, bookId: String, likerId: String): Flow<Resource<Boolean>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = usersCollection.document(targetUserId)
            .collection(FirebaseConstants.SUBCOLLECTION_USER_READINGS)
            .document(FirebaseConstants.DOCUMENT_ACTIVE_READING)
            .collection(FirebaseConstants.SUBCOLLECTION_ACTIVE_READING_LIKES)
            .document(likerId)
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
        val listener = booksCollection.document(bookId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val count = snapshot.getLong(FirebaseConstants.FIELD_LIKES_COUNT)?.toInt() ?: 0
                trySend(Resource.Success(count))
            } else {
                trySend(Resource.Success(0))
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun toggleBookmarkOnBook(bookId: String, currentUserId: String): Resource<Unit> {
        return try {
            val bookmarkDocRef = booksCollection.document(bookId).collection(SUBCOLLECTION_FAVORITED_BY).document(currentUserId)
            val bookDocRef = booksCollection.document(bookId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(bookmarkDocRef)
                if (snapshot.exists()) {
                    transaction.delete(bookmarkDocRef)
                    transaction.update(bookDocRef, "favoritesCount", FieldValue.increment(-1))
                } else {
                    transaction.set(bookmarkDocRef, mapOf("userId" to currentUserId, "timestamp" to FieldValue.serverTimestamp()))
                    transaction.update(bookDocRef, "favoritesCount", FieldValue.increment(1))
                }
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "toggleBookmarkOnBook: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de l'ajout/suppression du favori: ${e.localizedMessage}")
        }
    }

    override fun isBookBookmarkedByUser(bookId: String, currentUserId: String): Flow<Resource<Boolean>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = booksCollection.document(bookId).collection(SUBCOLLECTION_FAVORITED_BY).document(currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                    close(error); return@addSnapshotListener
                }
                trySend(Resource.Success(snapshot?.exists() == true))
            }
        awaitClose { listener.remove() }
    }

    override suspend fun rateBook(bookId: String, userId: String, rating: Float): Resource<Unit> {
        return try {
            val ratingDocRef = booksCollection.document(bookId).collection(SUBCOLLECTION_RATINGS).document(userId)
            val ratingData = mapOf(
                "userId" to userId,
                "rating" to rating,
                "timestamp" to FieldValue.serverTimestamp()
            )
            ratingDocRef.set(ratingData).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "rateBook: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de la notation: ${e.localizedMessage}")
        }
    }

    override fun getUserRatingForBook(bookId: String, userId: String): Flow<Resource<Float?>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = booksCollection.document(bookId).collection(SUBCOLLECTION_RATINGS).document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                    close(error); return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val rating = snapshot.getDouble("rating")?.toFloat()
                    trySend(Resource.Success(rating))
                } else {
                    trySend(Resource.Success(null))
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun toggleRecommendationOnBook(bookId: String, userId: String): Resource<Unit> {
        return try {
            val recommendationDocRef = booksCollection.document(bookId).collection(SUBCOLLECTION_RECOMMENDED_BY).document(userId)
            val bookDocRef = booksCollection.document(bookId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(recommendationDocRef)
                if (snapshot.exists()) {
                    transaction.delete(recommendationDocRef)
                    transaction.update(bookDocRef, "recommendationsCount", FieldValue.increment(-1))
                } else {
                    transaction.set(recommendationDocRef, mapOf("userId" to userId, "timestamp" to FieldValue.serverTimestamp()))
                    transaction.update(bookDocRef, "recommendationsCount", FieldValue.increment(1))
                }
            }.await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "toggleRecommendationOnBook: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de la recommandation: ${e.localizedMessage}")
        }
    }

    override fun isBookRecommendedByUser(bookId: String, userId: String): Flow<Resource<Boolean>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = booksCollection.document(bookId).collection(SUBCOLLECTION_RECOMMENDED_BY).document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Erreur réseau"))
                    close(error); return@addSnapshotListener
                }
                trySend(Resource.Success(snapshot?.exists() == true))
            }
        awaitClose { listener.remove() }
    }

    override suspend fun toggleLikeOnComment(bookId: String, commentId: String, currentUserId: String): Resource<Unit> {
        return try {
            val likeRef = booksCollection.document(bookId)
                .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS).document(commentId)
                .collection(FirebaseConstants.SUBCOLLECTION_LIKES).document(currentUserId)

            val likeDoc = likeRef.get().await()

            if (likeDoc.exists()) {
                likeRef.delete().await()
            } else {
                likeRef.set(mapOf("userId" to currentUserId, "timestamp" to FieldValue.serverTimestamp())).await()
            }
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