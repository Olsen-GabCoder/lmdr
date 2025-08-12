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
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsViewModel @Inject constructor(
    private val privateChatRepository: PrivateChatRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    // === DÉBUT DE LA CORRECTION ARCHITECTURALE ===
    // JUSTIFICATION : Utilisation du pattern "Backing Property".
    // _conversations est privé et mutable, modifiable uniquement par le ViewModel.
    private val _conversations = MutableStateFlow<Resource<List<Conversation>>>(Resource.Loading())
    // conversations est public et immuable (read-only), exposé à l'UI.
    val conversations = _conversations.asStateFlow()
    // === FIN DE LA CORRECTION ARCHITECTURALE ===

    private val _activeFilter = MutableStateFlow(ConversationFilterType.ALL)
    private val _searchQuery = MutableStateFlow("")

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    init {
        // Le Flow réactif qui combine les filtres et la recherche.
        // Au lieu d'utiliser .stateIn(), on utilise .onEach pour collecter
        // le résultat et l'assigner à notre backing property _conversations.
        val conversationsFlow: Flow<Resource<List<Conversation>>> = _activeFilter.flatMapLatest { filter ->
            if (currentUserId.isBlank()) {
                flowOf(Resource.Error("Utilisateur non authentifié."))
            } else {
                privateChatRepository.getFilteredUserConversations(currentUserId, filter)
            }
        }

        combine(
            conversationsFlow,
            _searchQuery
        ) { resource, query ->
            when (resource) {
                is Resource.Success -> {
                    val serverFilteredList = resource.data ?: emptyList()
                    if (query.isBlank()) {
                        Resource.Success(serverFilteredList)
                    } else {
                        val searchFilteredList = serverFilteredList.filter { conversation ->
                            val otherUserId = conversation.participantIds.firstOrNull { it != currentUserId }
                            val participantName = conversation.participantNames[otherUserId] ?: ""
                            participantName.contains(query, ignoreCase = true)
                        }
                        Resource.Success(searchFilteredList)
                    }
                }
                is Resource.Error -> resource
                is Resource.Loading -> resource
            }
        }.onEach { result ->
            // On met à jour la valeur de notre MutableStateFlow privé.
            _conversations.value = result
        }.launchIn(viewModelScope)
    }


    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filterType: String) {
        _activeFilter.value = filterType
    }

    fun toggleFavoriteStatus(conversationToToggle: Conversation) {
        val convId = conversationToToggle.id ?: return
        val currentConversationsResource = _conversations.value

        if (currentConversationsResource is Resource.Success) {
            currentConversationsResource.data?.let { dataList ->
                val currentList = dataList.toMutableList()
                val index = currentList.indexOfFirst { it.id == convId }

                if (index != -1) {
                    val updatedConversation = currentList[index].copy(isFavorite = !conversationToToggle.isFavorite)
                    currentList[index] = updatedConversation

                    if (_activeFilter.value == ConversationFilterType.FAVORITES && !updatedConversation.isFavorite) {
                        currentList.removeAt(index)
                    }

                    // === CORRECTION DE LA LIGNE DU CRASH ===
                    // On met à jour la valeur de notre backing property, ce qui est une opération sûre.
                    _conversations.value = Resource.Success(currentList)
                    // === FIN DE LA CORRECTION ===
                }
            }
        }

        viewModelScope.launch {
            privateChatRepository.updateFavoriteStatus(convId, !conversationToToggle.isFavorite)
        }
    }
}