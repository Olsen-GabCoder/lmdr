// Fichier complet : SocialRepository.kt

package com.lesmangeursdurouleau.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

data class PaginatedCommentsResponse(
    val comments: List<Comment>,
    val lastVisibleDoc: DocumentSnapshot?,
    val hasNextPage: Boolean
)

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
    suspend fun searchUsersByUsername(query: String, limit: Long): Resource<List<User>>


    // ============================================================================================
    // --- NOUVELLE SECTION : INTERACTIONS SUR LES LECTURES (CORRIGÉ ET SÉCURISÉ) ---
    // ============================================================================================

    // JUSTIFICATION DE L'AJOUT : Cette nouvelle méthode est le cœur de la correction de la régression fonctionnelle.
    // Elle restaure la réactivité en temps réel en retournant un `Flow`. Contrairement à une `suspend fun` qui
    // effectue une lecture unique, un `Flow` émettra une nouvelle liste de commentaires à chaque fois qu'une
    // modification (ajout/suppression de commentaire, mise à jour d'un compteur de likes via Cloud Function)
    // survient dans la base de données. C'est la fondation pour une UI instantanément synchronisée.
    fun getCommentsForReadingStream(ownerUserId: String, bookId: String): Flow<Resource<List<Comment>>>

    suspend fun addCommentOnReading(ownerUserId: String, bookId: String, comment: Comment): Resource<Unit>
    suspend fun getCommentsForReadingPaginated(ownerUserId: String, bookId: String, lastVisibleDoc: DocumentSnapshot?): Resource<PaginatedCommentsResponse>
    suspend fun deleteCommentOnReading(ownerUserId: String, bookId: String, commentId: String): Resource<Unit>
    suspend fun updateCommentOnReading(ownerUserId: String, bookId: String, commentId: String, newText: String): Resource<Unit>
    suspend fun reportCommentOnReading(ownerUserId: String, bookId: String, commentId: String, reportingUserId: String, reason: String): Resource<Unit>
    suspend fun toggleLikeOnCommentForReading(ownerUserId: String, bookId: String, commentId: String, currentUserId: String): Resource<Unit>
    fun isCommentLikedByUserOnReading(ownerUserId: String, bookId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>>


    // ============================================================================================
    // --- SECTION DÉPRÉCIÉE : INTERACTIONS SUR LES LIVRES (DÉFECTUEUX) ---
    // ============================================================================================

    @Deprecated(
        message = "FAILLE DE SÉCURITÉ : Utilise un chemin de données partagé. Utilisez addCommentOnReading.",
        replaceWith = ReplaceWith("addCommentOnReading(ownerUserId, bookId, comment)")
    )
    suspend fun addCommentOnBook(bookId: String, comment: Comment): Resource<Unit>

    @Deprecated("Obsolète, non performant et défectueux. Utilisez getCommentsForReadingStream.", ReplaceWith("getCommentsForReadingStream(ownerUserId, bookId)"))
    fun getCommentsForBook(bookId: String): Flow<Resource<List<Comment>>>

    @Deprecated(
        message = "FAILLE DE SÉCURITÉ : Utilise un chemin de données partagé. Utilisez getCommentsForReadingPaginated.",
        replaceWith = ReplaceWith("getCommentsForReadingPaginated(ownerUserId, bookId, lastVisibleDoc)")
    )
    suspend fun getCommentsForBookPaginated(bookId: String, lastVisibleDoc: DocumentSnapshot?): Resource<PaginatedCommentsResponse>

    @Deprecated(
        message = "FAILLE DE SÉCURITÉ : Utilise un chemin de données partagé. Utilisez deleteCommentOnReading.",
        replaceWith = ReplaceWith("deleteCommentOnReading(ownerUserId, bookId, commentId)")
    )
    suspend fun deleteCommentOnBook(bookId: String, commentId: String): Resource<Unit>

    @Deprecated(
        message = "FAILLE DE SÉCURITÉ : Utilise un chemin de données partagé. Utilisez updateCommentOnReading.",
        replaceWith = ReplaceWith("updateCommentOnReading(ownerUserId, bookId, commentId, newText)")
    )
    suspend fun updateCommentOnBook(bookId: String, commentId: String, newText: String): Resource<Unit>

    @Deprecated(
        message = "FAILLE DE SÉCURITÉ : Utilise un chemin de données partagé. Utilisez reportCommentOnReading.",
        replaceWith = ReplaceWith("reportCommentOnReading(ownerUserId, bookId, commentId, reportingUserId, reason)")
    )
    suspend fun reportComment(bookId: String, commentId: String, reportingUserId: String, reason: String): Resource<Unit>

    @Deprecated(
        message = "FAILLE DE SÉCURITÉ : Utilise un chemin de données partagé. Utilisez toggleLikeOnCommentForReading.",
        replaceWith = ReplaceWith("toggleLikeOnCommentForReading(ownerUserId, bookId, commentId, currentUserId)")
    )
    suspend fun toggleLikeOnComment(bookId: String, commentId: String, currentUserId: String): Resource<Unit>

    @Deprecated(
        message = "FAILLE DE SÉCURITÉ : Utilise un chemin de données partagé. Utilisez isCommentLikedByUserOnReading.",
        replaceWith = ReplaceWith("isCommentLikedByUserOnReading(ownerUserId, bookId, commentId, currentUserId)")
    )
    fun isCommentLikedByCurrentUser(bookId: String, commentId: String, currentUserId: String): Flow<Resource<Boolean>>


    // --- SECTIONS NON AFFECTÉES (INCHANGÉES) ---
    suspend fun toggleLikeOnReading(targetUserId: String, bookId: String, likerId: String): Resource<Unit>
    fun isReadingLikedByUser(targetUserId: String, bookId: String, likerId: String): Flow<Resource<Boolean>>

    suspend fun toggleLikeOnBook(bookId: String, currentUserId: String): Resource<Unit>
    fun isBookLikedByUser(bookId: String, currentUserId: String): Flow<Resource<Boolean>>
    fun getBookLikesCount(bookId: String): Flow<Resource<Int>>

    suspend fun toggleBookmarkOnBook(bookId: String, currentUserId: String): Resource<Unit>
    fun isBookBookmarkedByUser(bookId: String, currentUserId: String): Flow<Resource<Boolean>>

    suspend fun rateBook(bookId: String, userId: String, rating: Float): Resource<Unit>
    fun getUserRatingForBook(bookId: String, userId: String): Flow<Resource<Float?>>

    suspend fun toggleRecommendationOnBook(bookId: String, userId: String): Resource<Unit>
    fun isBookRecommendedByUser(bookId: String, userId: String): Flow<Resource<Boolean>>
}