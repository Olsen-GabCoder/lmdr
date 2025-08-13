// CRÉEZ OU REMPLACEZ LE FICHIER : ui/members/NewConversationViewModel.kt
package com.lesmangeursdurouleau.app.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewConversationViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    // Flow qui expose l'état de la liste des utilisateurs (chargement, succès, erreur)
    val users: StateFlow<Resource<List<User>>> = _searchQuery
        .flatMapLatest { query ->
            // Le Flow se met à jour à chaque changement du filtre ou de la recherche
            socialRepository.getMutualContacts(currentUserId)
                .map { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            val contacts = resource.data ?: emptyList()
                            val filteredList = if (query.isBlank()) {
                                contacts
                            } else {
                                contacts.filter {
                                    it.username.contains(query, ignoreCase = true)
                                }
                            }
                            Resource.Success(filteredList.sortedBy { it.username })
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

    /**
     * Met à jour le terme de recherche pour filtrer la liste des contacts.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}