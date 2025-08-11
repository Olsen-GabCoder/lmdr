package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.EnrichedUserListItem
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// MODIFIÉ : Le type de données dans PagedSuccess est maintenant EnrichedUserListItem
sealed class MembersUiState {
    object Loading : MembersUiState()
    data class Error(val message: String) : MembersUiState()
    data class PagedSuccess(val pagedUsers: Flow<PagingData<EnrichedUserListItem>>) : MembersUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MembersViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val socialRepository: SocialRepository,
    private val firebaseAuth: FirebaseAuth, // NOUVELLE DÉPENDANCE pour l'ID de l'utilisateur actuel
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "MembersViewModel"
    }

    private val currentUserId: String? = firebaseAuth.currentUser?.uid
    private val _searchQuery = MutableStateFlow("")

    private val listType: String? = savedStateHandle.get<String>("listType")
    private val targetUserId: String? = savedStateHandle.get<String>("userId")

    // NOUVEAU : Un StateFlow pour contenir la liste des IDs que l'utilisateur actuel suit.
    // Il est chargé une seule fois et partagé pour toutes les opérations.
    private val currentUserFollowingIds: StateFlow<Set<String>> =
        if (currentUserId == null) {
            MutableStateFlow(emptySet())
        } else {
            // Utilise l'ancienne méthode non-paginée car nous avons besoin de TOUS les IDs en mémoire.
            // C'est un compromis acceptable car on ne charge que des IDs (très léger).
            socialRepository.getFollowingUsers(currentUserId)
                .map { resource ->
                    (resource as? Resource.Success)?.data?.map { it.uid }?.toSet() ?: emptySet()
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
        }

    val uiState: StateFlow<MembersUiState> = combine(
        _searchQuery.debounce(300L),
        currentUserFollowingIds // On combine avec la liste des IDs suivis
    ) { query, followingIds ->
        Pair(query, followingIds)
    }.flatMapLatest { (query, followingIds) ->
        Log.d(TAG, "Détection d'un changement d'état. listType: $listType, query: '$query'")

        val pagedUsersFlow: Flow<PagingData<EnrichedUserListItem>> = when (listType) {
            "followers" -> {
                if (targetUserId.isNullOrBlank()) return@flatMapLatest flow { emit(MembersUiState.Error("ID utilisateur cible manquant.")) }
                socialRepository.getFollowersUsersPaginated(targetUserId)
            }
            "following" -> {
                if (targetUserId.isNullOrBlank()) return@flatMapLatest flow { emit(MembersUiState.Error("ID utilisateur cible manquant.")) }
                socialRepository.getFollowingUsersPaginated(targetUserId)
            }
            else -> {
                if (query.isNotBlank()) userProfileRepository.searchUsersPaginated(query)
                else userProfileRepository.getAllUsersPaginated()
            }
        }

        // Cœur de la logique : on transforme chaque page d'utilisateurs pour y ajouter l'état de suivi.
        val enrichedFlow = pagedUsersFlow.map { pagingData ->
            pagingData.map { user ->
                user.copy(
                    // L'utilisateur est suivi si son ID est dans notre liste d'IDs suivis.
                    isFollowedByCurrentUser = followingIds.contains(user.uid)
                )
            }
        }.cachedIn(viewModelScope)

        flow { emit(MembersUiState.PagedSuccess(enrichedFlow)) }
    }
        .catch { e ->
            Log.e(TAG, "Exception dans le flow principal du ViewModel", e)
            emit(MembersUiState.Error("Erreur technique: ${e.localizedMessage}"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MembersUiState.Loading
        )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    // NOUVEAU : Fonctions pour gérer les actions de suivi depuis l'UI.
    fun followUser(targetUserId: String) {
        if (currentUserId == null) return
        viewModelScope.launch {
            socialRepository.followUser(currentUserId, targetUserId)
            // Note : L'UI se mettra à jour automatiquement car currentUserFollowingIds va émettre une nouvelle valeur.
        }
    }

    fun unfollowUser(targetUserId: String) {
        if (currentUserId == null) return
        viewModelScope.launch {
            socialRepository.unfollowUser(currentUserId, targetUserId)
        }
    }
}