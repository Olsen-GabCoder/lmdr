// PRÊT À COLLER - Fichier SocialRepository.kt complet et MODIFIÉ
package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository centralisé pour toutes les interactions sociales.
 */
interface SocialRepository {

    // --- SECTION: GRAPHE SOCIAL ---
    suspend fun followUser(currentUserId: String, targetUserId: String): Resource<Unit>
    suspend fun unfollowUser(currentUserId: String, targetUserId: String): Resource<Unit>
    fun isFollowing(currentUserId: String, targetUserId: String): Flow<Resource<Boolean>>
    fun getFollowingUsers(userId: String): Flow<Resource<List<User>>>
    fun getFollowersUsers(userId: String): Flow<Resource<List<User>>>
    // AJOUT : Nouvelle méthode pour la recherche de mentions.
    suspend fun searchUsersByUsername(query: String, limit: Long): Resource<List<User>>


    // --- SECTION: INTERACTIONS SUR LES LIVRES (COMMENTAIRES) ---
    suspend fun addCommentOnBook(bookId: String, comment: Comment): Resource<Unit>
    fun getCommentsForBook(bookId: String): Flow<Resource<List<Comment>>>
    suspend fun deleteCommentOnBook(bookId: String, commentId: String): Resource<Unit>
    suspend fun updateCommentOnBook(bookId: String, commentId: String, newText: String): Resource<Unit>
    suspend fun reportComment(bookId: String, commentId: String, reportingUserId: String, reason: String): Resource<Unit>

    // --- SECTION: INTERACTIONS SUR LA LECTURE ACTIVE (Social & Performant) ---
    suspend fun toggleLikeOnReading(targetUserId: String, bookId: String, likerId: String): Resource<Unit>
    fun isReadingLikedByUser(targetUserId: String, bookId: String, likerId: String): Flow<Resource<Boolean>>

    // --- SECTION: INTERACTIONS SUR UN LIVRE (Général) ---
    suspend fun toggleLikeOnBook(bookId: String, currentUserId: String): Resource<Unit>
    fun isBookLikedByUser(bookId: String, currentUserId: String): Flow<Resource<Boolean>>
    fun getBookLikesCount(bookId: String): Flow<Resource<Int>>

    // --- SECTION: INTERACTIONS SUR LES LIVRES (FAVORIS) ---
    suspend fun toggleBookmarkOnBook(bookId: String, currentUserId: String): Resource<Unit>
    fun isBookBookmarkedByUser(bookId: String, currentUserId: String): Flow<Resource<Boolean>>

    // --- SECTION: INTERACTIONS SUR LES LIVRES (NOTATION) ---
    suspend fun rateBook(bookId: String, userId: String, rating: Float): Resource<Unit>
    fun getUserRatingForBook(bookId: String, userId: String): Flow<Resource<Float?>>

    // --- SECTION: INTERACTIONS SUR LES LIVRES (RECOMMANDATIONS) ---
    suspend fun toggleRecommendationOnBook(bookId: String, userId: String): Resource<Unit>
    fun isBookRecommendedByUser(bookId: String, userId: String): Flow<Resource<Boolean>>

    // --- SECTION: INTERACTIONS SUR LES COMMENTAIRES ---
    suspend fun toggleLikeOnComment(bookId: String, commentId: String, currentUserId: String): Resource<Unit>
    fun isCommentLikedByCurrentUser(bookId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>>
}