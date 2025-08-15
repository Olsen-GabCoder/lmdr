// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PrivateChatRepository.kt
package com.lesmangeursdurouleau.app.data.repository

import android.net.Uri
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.ui.members.ConversationFilterType
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Deprecated("La pagination manuelle a été remplacée par un listener temps-réel unique pour plus de robustesse.")
data class PaginatedMessagesResponse(
    val messages: List<PrivateMessage>,
    val lastVisibleMessageId: String?,
    val hasMoreMessages: Boolean
)

interface PrivateChatRepository {

    fun getConversation(conversationId: String): Flow<Resource<Conversation>>

    @Deprecated(
        "Non-performant car charge toutes les conversations. Remplacé par getFilteredUserConversations.",
        ReplaceWith("getFilteredUserConversations(userId, ConversationFilterType.ALL)")
    )
    fun getUserConversations(userId: String): Flow<Resource<List<Conversation>>>

    fun getFilteredUserConversations(userId: String, filterType: String): Flow<Resource<List<Conversation>>>

    suspend fun createOrGetConversation(currentUserId: String, targetUserId: String): Resource<String>

    fun getConversationMessages(conversationId: String): Flow<Resource<List<PrivateMessage>>>

    @Deprecated("La pagination manuelle a été remplacée par un listener temps-réel unique pour plus de robustesse.")
    suspend fun getConversationMessagesPaginated(
        conversationId: String,
        lastVisibleMessageId: String?,
        pageSize: Int
    ): Resource<PaginatedMessagesResponse>

    @Deprecated("La pagination manuelle a été remplacée par un listener temps-réel unique pour plus de robustesse.")
    fun getConversationMessagesAfter(conversationId: String, afterTimestamp: Date?): Flow<Resource<List<PrivateMessage>>>

    suspend fun sendPrivateMessage(conversationId: String, message: PrivateMessage): Resource<Unit>

    suspend fun sendImageMessage(conversationId: String, imageData: ByteArray, text: String? = null): Resource<Unit>

    suspend fun deletePrivateMessage(conversationId: String, messageId: String): Resource<Unit>
    suspend fun markConversationAsRead(conversationId: String): Resource<Unit>
    suspend fun addOrUpdateReaction(conversationId: String, messageId: String, userId: String, emoji: String): Resource<Unit>
    suspend fun editPrivateMessage(conversationId: String, messageId: String, newText: String): Resource<Unit>
    suspend fun updateFavoriteStatus(conversationId: String, isFavorite: Boolean): Resource<Unit>
    suspend fun updateTypingStatus(conversationId: String, userId: String, isTyping: Boolean): Resource<Unit>
    suspend fun updateUserActiveStatus(conversationId: String, userId: String, isActive: Boolean): Resource<Unit>
    suspend fun completeChallenge(conversationId: String, challengeId: String, bonusPoints: Int): Resource<Unit>

    // === DÉBUT DE L'AJOUT ===
    suspend fun updatePinnedStatus(conversationIds: List<String>, isPinned: Boolean): Resource<Unit>
    suspend fun updateArchivedStatus(conversationIds: List<String>, isArchived: Boolean): Resource<Unit>
    suspend fun deleteConversations(conversationIds: List<String>): Resource<Unit>
    // === FIN DE L'AJOUT ===
}