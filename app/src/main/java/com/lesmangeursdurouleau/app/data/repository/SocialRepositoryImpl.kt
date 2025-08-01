// Fichier complet : SocialRepositoryImpl.kt

package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
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
        private const val COMMENTS_PAGE_SIZE = 15
        private const val SUBCOLLECTION_READINGS = "readings"
        private const val SUBCOLLECTION_FAVORITED_BY = "favoritedBy"
        private const val SUBCOLLECTION_RATINGS = "ratings"
        private const val SUBCOLLECTION_RECOMMENDED_BY = "recommendedBy"
        private const val SUBCOLLECTION_REPORTS = "reports"
    }

    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)
    private val booksCollection = firestore.collection(FirebaseConstants.COLLECTION_BOOKS)

    // --- GRAPHE SOCIAL (INCHANGÉ) ---
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

    override suspend fun searchUsersByUsername(query: String, limit: Long): Resource<List<User>> {
        if (query.isBlank()) {
            return Resource.Success(emptyList())
        }
        return try {
            val snapshot = usersCollection
                .orderBy("username")
                .startAt(query)
                .endAt(query + '\uf8ff')
                .limit(limit)
                .get()
                .await()
            val users = snapshot.documents.mapNotNull { it.toObject(User::class.java)?.copy(uid = it.id) }
            Resource.Success(users)
        } catch (e: Exception) {
            Log.e(TAG, "searchUsersByUsername: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors de la recherche: ${e.localizedMessage}")
        }
    }


    // ============================================================================================
    // --- IMPLÉMENTATIONS : INTERACTIONS SUR LES LECTURES (CORRIGÉ ET SÉCURISÉ) ---
    // ============================================================================================

    private fun getCommentsCollectionForReading(ownerUserId: String, bookId: String) =
        usersCollection.document(ownerUserId).collection(SUBCOLLECTION_READINGS).document(bookId)
            .collection(FirebaseConstants.SUBCOLLECTION_COMMENTS)

    // JUSTIFICATION DE L'IMPLÉMENTATION : Cette nouvelle fonction implémente le contrat `getCommentsForReadingStream`
    // pour restaurer la réactivité en temps réel.
    // 1. `callbackFlow` est le constructeur idiomatique pour convertir un écouteur (listener) en `Flow` de manière sécurisée.
    // 2. `addSnapshotListener` est utilisé à la place de `.get()` pour s'abonner aux changements de la collection de commentaires en temps réel.
    // 3. La requête cible le chemin sécurisé `/users/{ownerUserId}/readings/{bookId}/comments`, assurant que seul le bon silo de données est écouté.
    // 4. `awaitClose { listener.remove() }` est une mesure de sécurité cruciale pour éviter les fuites de mémoire en détachant
    //    l'écouteur lorsque le `Flow` n'est plus collecté par le ViewModel.
    override fun getCommentsForReadingStream(ownerUserId: String, bookId: String): Flow<Resource<List<Comment>>> = callbackFlow {
        trySend(Resource.Loading())

        val query = getCommentsCollectionForReading(ownerUserId, bookId)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "getCommentsForReadingStream: écoute échouée.", error)
                trySend(Resource.Error(error.localizedMessage ?: "Erreur de chargement des commentaires"))
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val comments = snapshot.documents.mapNotNull {
                    try {
                        it.toObject(Comment::class.java)?.copy(commentId = it.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Impossible de désérialiser le commentaire ${it.id}. Erreur: ${e.message}")
                        null // Ce commentaire corrompu sera ignoré par mapNotNull, rendant le flux résilient.
                    }
                }
                trySend(Resource.Success(comments))
            }
        }
        awaitClose {
            Log.d(TAG, "Fermeture du listener pour getCommentsForReadingStream.")
            listener.remove()
        }
    }

    override suspend fun addCommentOnReading(ownerUserId: String, bookId: String, comment: Comment): Resource<Unit> {
        return try {
            val commentData = mutableMapOf<String, Any?>(
                "userId" to comment.userId,
                "userName" to comment.userName,
                "userPhotoUrl" to comment.userPhotoUrl,
                "bookId" to comment.bookId,
                "commentText" to comment.commentText,
                "timestamp" to FieldValue.serverTimestamp()
            )
            if (comment.parentCommentId != null) {
                commentData["parentCommentId"] = comment.parentCommentId
            }
            getCommentsCollectionForReading(ownerUserId, bookId).add(commentData).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addCommentOnReading: Erreur: ${e.message}", e)
            Resource.Error("Erreur d'ajout du commentaire: ${e.localizedMessage}")
        }
    }

    override suspend fun getCommentsForReadingPaginated(ownerUserId: String, bookId: String, lastVisibleDoc: DocumentSnapshot?): Resource<PaginatedCommentsResponse> {
        return try {
            var query = getCommentsCollectionForReading(ownerUserId, bookId)
                .orderBy("timestamp", Query.Direction.DESCENDING)

            if (lastVisibleDoc != null) {
                query = query.startAfter(lastVisibleDoc)
            }

            val snapshot = query.limit(COMMENTS_PAGE_SIZE.toLong() + 1).get().await()
            val documents = snapshot.documents

            val comments = documents.mapNotNull {
                try {
                    it.toObject(Comment::class.java)?.copy(commentId = it.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Impossible de désérialiser le commentaire ${it.id}. Erreur: ${e.message}")
                    null
                }
            }

            val hasNextPage = documents.size > COMMENTS_PAGE_SIZE
            val results = if (hasNextPage) comments.dropLast(1) else comments
            val newLastVisibleDoc = if (results.isNotEmpty()) documents[results.size - 1] else null

            val response = PaginatedCommentsResponse(results, newLastVisibleDoc, hasNextPage)
            Resource.Success(response)
        } catch (e: Exception) {
            Log.e(TAG, "getCommentsForReadingPaginated: Erreur: ${e.message}", e)
            Resource.Error("Erreur de chargement des commentaires: ${e.localizedMessage}")
        }
    }

    override suspend fun deleteCommentOnReading(ownerUserId: String, bookId: String, commentId: String): Resource<Unit> {
        return try {
            getCommentsCollectionForReading(ownerUserId, bookId).document(commentId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteCommentOnReading: Erreur: ${e.message}", e)
            Resource.Error("Erreur de suppression: ${e.localizedMessage}")
        }
    }

    override suspend fun updateCommentOnReading(ownerUserId: String, bookId: String, commentId: String, newText: String): Resource<Unit> {
        return try {
            val updates = mapOf(
                "commentText" to newText,
                "isEdited" to true,
                "lastEditTimestamp" to FieldValue.serverTimestamp()
            )
            getCommentsCollectionForReading(ownerUserId, bookId).document(commentId).update(updates).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateCommentOnReading: Erreur: ${e.message}", e)
            Resource.Error("Erreur de mise à jour du commentaire: ${e.localizedMessage}")
        }
    }

    override suspend fun reportCommentOnReading(ownerUserId: String, bookId: String, commentId: String, reportingUserId: String, reason: String): Resource<Unit> {
        return try {
            val reportRef = getCommentsCollectionForReading(ownerUserId, bookId).document(commentId)
                .collection(SUBCOLLECTION_REPORTS).document(reportingUserId)
            val reportData = mapOf(
                "reportingUserId" to reportingUserId,
                "reason" to reason,
                "timestamp" to FieldValue.serverTimestamp()
            )
            reportRef.set(reportData).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "reportCommentOnReading: Erreur: ${e.message}", e)
            Resource.Error("Erreur lors du signalement: ${e.localizedMessage}")
        }
    }

    override suspend fun toggleLikeOnCommentForReading(ownerUserId: String, bookId: String, commentId: String, currentUserId: String): Resource<Unit> {
        return try {
            val likeRef = getCommentsCollectionForReading(ownerUserId, bookId).document(commentId)
                .collection(FirebaseConstants.SUBCOLLECTION_LIKES).document(currentUserId)
            val likeDoc = likeRef.get().await()
            if (likeDoc.exists()) {
                likeRef.delete().await()
            } else {
                likeRef.set(mapOf("userId" to currentUserId, "timestamp" to FieldValue.serverTimestamp())).await()
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "toggleLikeOnCommentForReading: Erreur: ${e.message}", e)
            Resource.Error("Erreur de like sur commentaire: ${e.localizedMessage}")
        }
    }

    override fun isCommentLikedByUserOnReading(ownerUserId: String, bookId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = getCommentsCollectionForReading(ownerUserId, bookId).document(commentId)
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


    // ============================================================================================
    // --- IMPLÉMENTATIONS DÉPRÉCIÉES (DÉSACTIVÉES) ---
    // ============================================================================================

    @Deprecated("FAILLE DE SÉCURITÉ")
    override suspend fun addCommentOnBook(bookId: String, comment: Comment): Resource<Unit> {
        throw NotImplementedError("FAILLE DE SÉCURITÉ : N'utilisez plus cette méthode. Utilisez addCommentOnReading.")
    }

    @Deprecated("Obsolète")
    override fun getCommentsForBook(bookId: String): Flow<Resource<List<Comment>>> {
        throw NotImplementedError("Obsolète et défectueux. Utilisez getCommentsForReadingStream.")
    }

    @Deprecated("FAILLE DE SÉCURITÉ")
    override suspend fun getCommentsForBookPaginated(bookId: String, lastVisibleDoc: DocumentSnapshot?): Resource<PaginatedCommentsResponse> {
        throw NotImplementedError("FAILLE DE SÉCURITÉ : N'utilisez plus cette méthode. Utilisez getCommentsForReadingPaginated.")
    }

    @Deprecated("FAILLE DE SÉCURITÉ")
    override suspend fun deleteCommentOnBook(bookId: String, commentId: String): Resource<Unit> {
        throw NotImplementedError("FAILLE DE SÉCURITÉ : N'utilisez plus cette méthode. Utilisez deleteCommentOnReading.")
    }

    @Deprecated("FAILLE DE SÉCURITÉ")
    override suspend fun updateCommentOnBook(bookId: String, commentId: String, newText: String): Resource<Unit> {
        throw NotImplementedError("FAILLE DE SÉCURITÉ : N'utilisez plus cette méthode. Utilisez updateCommentOnReading.")
    }

    @Deprecated("FAILLE DE SÉCURITÉ")
    override suspend fun reportComment(bookId: String, commentId: String, reportingUserId: String, reason: String): Resource<Unit> {
        throw NotImplementedError("FAILLE DE SÉCURITÉ : N'utilisez plus cette méthode. Utilisez reportCommentOnReading.")
    }

    @Deprecated("FAILLE DE SÉCURITÉ")
    override suspend fun toggleLikeOnComment(bookId: String, commentId: String, currentUserId: String): Resource<Unit> {
        throw NotImplementedError("FAILLE DE SÉCURITÉ : N'utilisez plus cette méthode. Utilisez toggleLikeOnCommentForReading.")
    }

    @Deprecated("FAILLE DE SÉCURITÉ")
    override fun isCommentLikedByCurrentUser(bookId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>> {
        throw NotImplementedError("FAILLE DE SÉCURITÉ : N'utilisez plus cette méthode. Utilisez isCommentLikedByUserOnReading.")
    }


    // --- SECTIONS NON AFFECTÉES (INCHANGÉES) ---
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
}