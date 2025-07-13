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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

sealed class MembersUiState {
    object Loading : MembersUiState()
    data class Success(val users: List<User>) : MembersUiState()
    data class Error(val message: String) : MembersUiState()
    data class PagedSuccess(val pagedUsers: Flow<PagingData<UserListItem>>) : MembersUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MembersViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val socialRepository: SocialRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "MembersViewModel"
    }

    /**
     * JUSTIFICATION DE L'AJOUT : Un StateFlow privé est ajouté pour contenir le terme de recherche.
     * Il servira de source de vérité pour la requête de l'utilisateur.
     */
    private val _searchQuery = MutableStateFlow("")

    /**
     * JUSTIFICATION DE LA MODIFICATION : Le StateFlow principal est maintenant reconstruit
     * à partir de la combinaison de DEUX sources : les arguments de navigation ET le terme de recherche.
     * Le `debounce` est ajouté sur la requête de recherche pour éviter de surcharger Firestore en
     * lançant une recherche à chaque caractère tapé. La logique de `when` est mise à jour pour
     * appeler soit la recherche paginée, soit la liste paginée complète.
     */
    val uiState: StateFlow<MembersUiState> = combine(
        savedStateHandle.getStateFlow<String?>("listType", null),
        _searchQuery.debounce(300L)
    ) { listType, query ->
        Pair(listType, query)
    }.flatMapLatest { (listType, query) ->
        val targetUserId = savedStateHandle.get<String?>("userId")
        Log.d(TAG, "Détection d'un changement d'état. listType: $listType, query: '$query'")

        when (listType) {
            "followers" -> {
                // La recherche côté client pourrait être implémentée ici si nécessaire.
                getFollowListFlow(
                    targetUserId,
                    errorMessage = "ID utilisateur cible manquant pour les followers.",
                    fetcher = socialRepository::getFollowersUsers
                )
            }
            "following" -> {
                // La recherche côté client pourrait être implémentée ici si nécessaire.
                getFollowListFlow(
                    targetUserId,
                    errorMessage = "ID utilisateur cible manquant pour les abonnements.",
                    fetcher = socialRepository::getFollowingUsers
                )
            }
            else -> {
                // Cas de la liste "tous les membres" qui supporte la recherche paginée.
                val pagedUsersFlow = if (query.isNotBlank()) {
                    Log.d(TAG, "Recherche de la liste paginée avec la requête: '$query'")
                    userProfileRepository.searchUsersPaginated(query)
                } else {
                    Log.d(TAG, "Chargement de la liste paginée optimisée de tous les utilisateurs.")
                    userProfileRepository.getAllUsersPaginated()
                }.cachedIn(viewModelScope) // Le cache est appliqué au résultat du if/else

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

    /**
     * JUSTIFICATION DE L'AJOUT : Nouvelle fonction publique pour que l'UI (Fragment) puisse notifier
     * le ViewModel d'un changement dans la requête de recherche.
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private fun getFollowListFlow(
        targetUserId: String?,
        errorMessage: String,
        fetcher: (String) -> Flow<Resource<List<User>>>
    ): Flow<MembersUiState> {
        if (targetUserId.isNullOrBlank()) {
            return flow { emit(MembersUiState.Error(errorMessage)) }
        }
        return fetcher(targetUserId).mapToUiState()
    }

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