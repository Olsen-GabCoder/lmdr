// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ConversationsViewModel.kt
package com.lesmangeursdurouleau.app.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

object ConversationFilterType {
    const val ALL = "ALL"
    const val UNREAD = "UNREAD"
    const val FAVORITES = "FAVORITES"
    const val GROUPS = "GROUPS"
    const val ARCHIVED = "ARCHIVED"
    const val PINNED = "PINNED"
}

sealed class ConversationListEvent {
    data class ShowSuccessMessage(val message: String) : ConversationListEvent()
    data class ShowErrorMessage(val message: String) : ConversationListEvent()
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsViewModel @Inject constructor(
    private val privateChatRepository: PrivateChatRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _conversations = MutableStateFlow<Resource<List<Conversation>>>(Resource.Loading())
    val conversations: StateFlow<Resource<List<Conversation>>> = _conversations.asStateFlow()

    private val _activeFilter = MutableStateFlow(ConversationFilterType.ALL)
    private val _searchQuery = MutableStateFlow("")

    private val _unreadConversationsCount = MutableStateFlow(0)
    val unreadConversationsCount: StateFlow<Int> = _unreadConversationsCount.asStateFlow()

    private val _events = MutableSharedFlow<ConversationListEvent>()
    val events = _events.asSharedFlow()

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    init {
        val baseConversationsFlow = _activeFilter.flatMapLatest { filter ->
            if (currentUserId.isBlank()) {
                flowOf(Resource.Error("Utilisateur non authentifié."))
            } else {
                privateChatRepository.getFilteredUserConversations(currentUserId, filter)
            }
        }

        combine(
            baseConversationsFlow,
            _searchQuery,
            _activeFilter
        ) { resource, query, activeFilter ->
            when (resource) {
                is Resource.Success -> {
                    val allConversations = resource.data ?: emptyList()

                    // Étape 1: Filtrage par type de chip
                    val typeFilteredList = when (activeFilter) {
                        ConversationFilterType.UNREAD -> allConversations.filter { (it.unreadCount[currentUserId] ?: 0) > 0 }
                        ConversationFilterType.FAVORITES -> allConversations.filter { it.isFavorite }
                        ConversationFilterType.PINNED -> allConversations.filter { it.isPinned }
                        // === DÉBUT DE LA CORRECTION : Logique de filtre pour les groupes ===
                        ConversationFilterType.GROUPS -> allConversations.filter { it.participantIds.size > 2 }
                        // === FIN DE LA CORRECTION ===
                        else -> allConversations
                    }

                    // Étape 2: Filtrage par recherche textuelle
                    val searchFilteredList = if (query.isBlank()) {
                        typeFilteredList
                    } else {
                        typeFilteredList.filter { conversation ->
                            val otherUserId = conversation.participantIds.firstOrNull { it != currentUserId }
                            val participantName = conversation.participantNames[otherUserId] ?: ""
                            participantName.contains(query, ignoreCase = true)
                        }
                    }

                    // Étape 3: Tri final
                    val sortedList = searchFilteredList.sortedWith(
                        compareByDescending<Conversation> { it.isPinned }
                            .thenByDescending { it.lastMessageTimestamp }
                    )
                    Resource.Success(sortedList)
                }
                is Resource.Error -> resource
                is Resource.Loading -> resource
            }
        }.onEach { result ->
            _conversations.value = result
        }.launchIn(viewModelScope)

        if (currentUserId.isNotBlank()) {
            privateChatRepository.getFilteredUserConversations(currentUserId, ConversationFilterType.ALL)
                .onEach { resource ->
                    if (resource is Resource.Success) {
                        val unarchivedConversations = resource.data ?: emptyList()
                        val totalUnread = unarchivedConversations.count { (it.unreadCount[currentUserId] ?: 0) > 0 }
                        _unreadConversationsCount.value = totalUnread
                    }
                }.launchIn(viewModelScope)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filterType: String) {
        _activeFilter.value = filterType
    }

    fun toggleFavoriteStatus(conversations: List<Conversation>) {
        if (conversations.isEmpty()) return
        viewModelScope.launch {
            val shouldBeFavorite = !conversations.first().isFavorite
            val ids = conversations.mapNotNull { it.id }
            ids.forEach { id ->
                privateChatRepository.updateFavoriteStatus(id, shouldBeFavorite)
            }
        }
    }

    fun pinConversations(conversations: List<Conversation>) {
        viewModelScope.launch {
            val ids = conversations.mapNotNull { it.id }
            if (ids.isEmpty()) return@launch
            val shouldBePinned = !conversations.first().isPinned
            val result = privateChatRepository.updatePinnedStatus(ids, shouldBePinned)
            if (result is Resource.Success) {
                val message = if(shouldBePinned) "${ids.size} conversation(s) épinglée(s)" else "${ids.size} conversation(s) désépinglée(s)"
                _events.emit(ConversationListEvent.ShowSuccessMessage(message))
            } else {
                _events.emit(ConversationListEvent.ShowErrorMessage(result.message ?: "Erreur lors de l'épinglage"))
            }
        }
    }

    fun archiveConversations(conversations: List<Conversation>) {
        viewModelScope.launch {
            val ids = conversations.mapNotNull { it.id }
            if (ids.isEmpty()) return@launch
            val result = privateChatRepository.updateArchivedStatus(ids, true)
            if (result is Resource.Success) {
                _events.emit(ConversationListEvent.ShowSuccessMessage("${ids.size} conversation(s) archivée(s)"))
            } else {
                _events.emit(ConversationListEvent.ShowErrorMessage(result.message ?: "Erreur lors de l'archivage"))
            }
        }
    }

    fun deleteConversations(conversations: List<Conversation>) {
        viewModelScope.launch {
            val ids = conversations.mapNotNull { it.id }
            if (ids.isEmpty()) return@launch
            val result = privateChatRepository.deleteConversations(ids)
            if (result is Resource.Success) {
                _events.emit(ConversationListEvent.ShowSuccessMessage("${ids.size} conversation(s) supprimée(s)"))
            } else {
                _events.emit(ConversationListEvent.ShowErrorMessage(result.message ?: "Erreur lors de la suppression"))
            }
        }
    }
}