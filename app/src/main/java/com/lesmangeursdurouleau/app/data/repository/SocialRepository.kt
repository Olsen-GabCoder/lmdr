// PRÊT À COLLER - Fichier 100% complet et validé
package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository centralisé pour toutes les interactions sociales :
 * 1. Graphe social (followers/following).
 * 2. Interactions sur les livres (commentaires, likes).
 */
interface SocialRepository {

    // --- SECTION: GRAPHE SOCIAL ---
    suspend fun followUser(currentUserId: String, targetUserId: String): Resource<Unit>
    suspend fun unfollowUser(currentUserId: String, targetUserId: String): Resource<Unit>
    fun isFollowing(currentUserId: String, targetUserId: String): Flow<Resource<Boolean>>
    fun getFollowingUsers(userId: String): Flow<Resource<List<User>>>
    fun getFollowersUsers(userId: String): Flow<Resource<List<User>>>

    // --- SECTION: INTERACTIONS SUR LES LIVRES (COMMENTAIRES) ---
    suspend fun addCommentOnBook(bookId: String, comment: Comment): Resource<Unit>
    fun getCommentsForBook(bookId: String): Flow<Resource<List<Comment>>>
    suspend fun deleteCommentOnBook(bookId: String, commentId: String): Resource<Unit>

    // --- SECTION: INTERACTIONS SUR LES LIVRES (LIKES) ---
    suspend fun toggleLikeOnBook(bookId: String, currentUserId: String): Resource<Unit>
    fun isBookLikedByUser(bookId: String, currentUserId: String): Flow<Resource<Boolean>>
    fun getBookLikesCount(bookId: String): Flow<Resource<Int>>

    // --- SECTION: INTERACTIONS SUR LES COMMENTAIRES ---
    suspend fun toggleLikeOnComment(bookId: String, commentId: String, currentUserId: String): Resource<Unit>
    fun isCommentLikedByCurrentUser(bookId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>>
}