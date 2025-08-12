// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PrivateChatRepository.kt
package com.lesmangeursdurouleau.app.data.repository

import android.net.Uri
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.ui.members.ConversationFilterType
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import java.util.Date

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

    @Deprecated("Charge tous les messages. Utiliser le listener getConversationMessagesAfter pour le temps réel.", ReplaceWith("getConversationMessagesAfter(conversationId, null)"))
    fun getConversationMessages(conversationId: String): Flow<Resource<List<PrivateMessage>>>

    suspend fun getConversationMessagesPaginated(
        conversationId: String,
        lastVisibleMessageId: String?,
        pageSize: Int
    ): Resource<PaginatedMessagesResponse>

    // === DÉBUT DE L'AJOUT ===
    /**
     * Écoute en temps réel les messages arrivant APRÈS un certain timestamp.
     * Idéal pour ne récupérer que les nouveaux messages sans recharger tout l'historique.
     *
     * @param conversationId L'ID de la conversation.
     * @param afterTimestamp Le timestamp de référence. Si null, écoute tous les messages.
     * @return Un Flow de Resource contenant la liste des nouveaux messages.
     */
    fun getConversationMessagesAfter(conversationId: String, afterTimestamp: Date?): Flow<Resource<List<PrivateMessage>>>
    // === FIN DE L'AJOUT ===

    suspend fun sendPrivateMessage(conversationId: String, message: PrivateMessage): Resource<Unit>
    suspend fun sendImageMessage(conversationId: String, imageUri: Uri, text: String? = null): Resource<Unit>
    suspend fun deletePrivateMessage(conversationId: String, messageId: String): Resource<Unit>
    suspend fun markConversationAsRead(conversationId: String): Resource<Unit>
    suspend fun addOrUpdateReaction(conversationId: String, messageId: String, userId: String, emoji: String): Resource<Unit>
    suspend fun editPrivateMessage(conversationId: String, messageId: String, newText: String): Resource<Unit>
    suspend fun updateFavoriteStatus(conversationId: String, isFavorite: Boolean): Resource<Unit>
    suspend fun updateTypingStatus(conversationId: String, userId: String, isTyping: Boolean): Resource<Unit>
    suspend fun updateUserActiveStatus(conversationId: String, userId: String, isActive: Boolean): Resource<Unit>
    suspend fun completeChallenge(conversationId: String, challengeId: String, bonusPoints: Int): Resource<Unit>
}