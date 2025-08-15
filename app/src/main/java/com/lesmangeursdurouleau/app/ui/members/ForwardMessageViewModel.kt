// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ForwardMessageViewModel.kt
package com.lesmangeursdurouleau.app.ui.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.model.PrivateMessage
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ForwardEvent {
    data class Success(val conversationName: String) : ForwardEvent()
    data class Error(val message: String) : ForwardEvent()
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ForwardMessageViewModel @Inject constructor(
    private val privateChatRepository: PrivateChatRepository,
    private val firebaseAuth: FirebaseAuth,
    private val savedStateHandle: SavedStateHandle // Injecté pour accéder aux arguments
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    private val _events = MutableSharedFlow<ForwardEvent>()
    val events = _events.asSharedFlow()

    // Lecture des arguments passés au fragment
    private val messageText: String? = savedStateHandle.get("messageText")
    private val imageUrl: String? = savedStateHandle.get("imageUrl")

    val conversations: StateFlow<Resource<List<Conversation>>> = _searchQuery
        .flatMapLatest { query ->
            val baseFlow = if (currentUserId.isBlank()) {
                flowOf(Resource.Error("Utilisateur non authentifié."))
            } else {
                privateChatRepository.getFilteredUserConversations(currentUserId, ConversationFilterType.ALL)
            }

            baseFlow.map { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val allConversations = resource.data ?: emptyList()
                        val filteredList = if (query.isBlank()) {
                            allConversations
                        } else {
                            allConversations.filter { conversation ->
                                val participantName = conversation.participantNames.values.joinToString(" ")
                                participantName.contains(query, ignoreCase = true)
                            }
                        }
                        val sortedList = filteredList.sortedByDescending { it.lastMessageTimestamp }
                        Resource.Success(sortedList)
                    }
                    is Resource.Error -> resource
                    is Resource.Loading -> resource
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Resource.Loading()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun forwardMessage(destinationConversation: Conversation) {
        viewModelScope.launch {
            val destinationId = destinationConversation.id
            if (destinationId == null) {
                _events.emit(ForwardEvent.Error("ID de conversation de destination invalide."))
                return@launch
            }

            if (messageText == null && imageUrl == null) {
                _events.emit(ForwardEvent.Error("Aucun contenu de message à transférer."))
                return@launch
            }

            val messageToSend = PrivateMessage(
                senderId = currentUserId,
                text = messageText,
                imageUrl = imageUrl
                // Les autres champs comme les réactions, replyInfo, etc., ne sont pas transférés.
            )

            val result = privateChatRepository.sendPrivateMessage(destinationId, messageToSend)

            if (result is Resource.Success) {
                val otherUserId = destinationConversation.participantIds.firstOrNull { it != currentUserId }
                val conversationName = destinationConversation.participantNames[otherUserId] ?: "la conversation"
                _events.emit(ForwardEvent.Success(conversationName))
            } else {
                _events.emit(ForwardEvent.Error(result.message ?: "Une erreur est survenue lors de l'envoi."))
            }
        }
    }
}