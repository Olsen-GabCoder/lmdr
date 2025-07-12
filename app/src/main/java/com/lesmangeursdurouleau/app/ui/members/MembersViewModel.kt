// Fichier Modifié : MembersViewModel.kt

package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserListItem
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class MembersUiState {
    object Loading : MembersUiState()
    data class Success(val users: List<User>) : MembersUiState()
    data class Error(val message: String) : MembersUiState()

    /**
     * JUSTIFICATION DE LA MODIFICATION : L'état PagedSuccess est mis à jour pour contenir
     * un flux de `PagingData<UserListItem>`, le nouveau modèle de données optimisé.
     */
    data class PagedSuccess(val pagedUsers: Flow<PagingData<UserListItem>>) : MembersUiState()
}

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val socialRepository: SocialRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "MembersViewModel"
    }

    val uiState: StateFlow<MembersUiState> = savedStateHandle.getStateFlow<String?>("listType", null)
        .flatMapLatest { listType ->
            val targetUserId = savedStateHandle.get<String?>("userId")
            Log.d(TAG, "Détection d'un changement d'état. listType: $listType, targetUserId: $targetUserId")

            when (listType) {
                "followers" -> {
                    if (targetUserId.isNullOrBlank()) {
                        flow { emit(MembersUiState.Error("ID utilisateur cible manquant pour les followers.")) }
                    } else {
                        socialRepository.getFollowersUsers(targetUserId).mapToUiState()
                    }
                }
                "following" -> {
                    if (targetUserId.isNullOrBlank()) {
                        flow { emit(MembersUiState.Error("ID utilisateur cible manquant pour les abonnements.")) }
                    } else {
                        socialRepository.getFollowingUsers(targetUserId).mapToUiState()
                    }
                }
                else -> {
                    Log.d(TAG, "Chargement de la liste paginée optimisée de tous les utilisateurs.")
                    val pagedUsersFlow = userProfileRepository.getAllUsersPaginated().cachedIn(viewModelScope)
                    flow { emit(MembersUiState.PagedSuccess(pagedUsersFlow)) }
                }
            }
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

    private fun Flow<Resource<List<User>>>.mapToUiState(): Flow<MembersUiState> {
        return this.map { resource ->
            when(resource) {
                is Resource.Loading -> MembersUiState.Loading
                is Resource.Success -> MembersUiState.Success(resource.data ?: emptyList())
                is Resource.Error -> MembersUiState.Error(resource.message ?: "Erreur inconnue")
            }
        }
    }
}