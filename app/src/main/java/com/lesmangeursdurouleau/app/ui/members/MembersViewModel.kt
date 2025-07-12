// Fichier complet : MembersViewModel.kt

package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lesmangeursdurouleau.app.data.model.User
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

/**
 * JUSTIFICATION DE L'AJOUT : Une classe d'état scellée (sealed class) est introduite pour représenter
 * de manière explicite et robuste tous les états possibles de l'UI.
 * Cela remplace l'ancienne approche basée sur `Resource` qui mélangeait données et état.
 * Elle est conçue pour contenir soit une liste simple (pour followers/following),
 * soit un flux de données paginées (pour la liste de tous les membres).
 */
sealed class MembersUiState {
    object Loading : MembersUiState()
    data class Success(val users: List<User>) : MembersUiState()
    data class Error(val message: String) : MembersUiState()
    // Ce nouvel état contiendra le flux de données paginées pour le cas "tous les membres".
    data class PagedSuccess(val pagedUsers: Flow<PagingData<User>>) : MembersUiState()
}

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val socialRepository: SocialRepository,
    // JUSTIFICATION DE L'AJOUT : SavedStateHandle est injecté pour permettre au ViewModel
    // de réagir aux arguments de navigation de manière autonome, sans attendre un appel
    // impératif du Fragment. C'est la pierre angulaire de la refonte vers une architecture réactive.
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "MembersViewModel"
    }

    // JUSTIFICATION DE LA MODIFICATION : Le LiveData est remplacé par un StateFlow.
    // Il expose le nouvel état `MembersUiState` et est alimenté par une logique réactive
    // qui observe les arguments de navigation. `flatMapLatest` garantit que si les
    // arguments changent, l'ancienne collecte est annulée et une nouvelle est démarrée.
    // `stateIn` transforme le Flow froid en un StateFlow chaud, partageant la dernière
    // valeur avec tous les collecteurs et la conservant lors des changements de configuration.
    val uiState: StateFlow<MembersUiState> = savedStateHandle.getStateFlow<String?>("listType", null)
        .flatMapLatest { listType ->
            val targetUserId = savedStateHandle.get<String?>("userId")
            Log.d(TAG, "Détection d'un changement d'état. listType: $listType, targetUserId: $targetUserId")

            when (listType) {
                "followers" -> {
                    if (targetUserId.isNullOrBlank()) {
                        flow { emit(MembersUiState.Error("ID utilisateur cible manquant pour les followers.")) }
                    } else {
                        // La logique pour les followers reste non paginée pour l'instant.
                        socialRepository.getFollowersUsers(targetUserId).mapToUiState()
                    }
                }
                "following" -> {
                    if (targetUserId.isNullOrBlank()) {
                        flow { emit(MembersUiState.Error("ID utilisateur cible manquant pour les abonnements.")) }
                    } else {
                        // La logique pour les abonnements reste non paginée pour l'instant.
                        socialRepository.getFollowingUsers(targetUserId).mapToUiState()
                    }
                }
                else -> {
                    // JUSTIFICATION DE LA MODIFICATION : C'est ici que la faille critique est résolue.
                    // Nous appelons la nouvelle méthode paginée `getAllUsersPaginated`.
                    // Le résultat est encapsulé dans l'état `PagedSuccess`. Le `.cachedIn(viewModelScope)`
                    // est crucial pour la performance, car il met en cache les données paginées,
                    // évitant de tout recharger depuis le début lors de la rotation de l'écran.
                    Log.d(TAG, "Chargement de la liste paginée de tous les utilisateurs.")
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

    /**
     * JUSTIFICATION DE LA SUPPRESSION : La fonction `fetchMembers` est supprimée.
     * Le ViewModel est maintenant réactif et autonome. Il n'a plus besoin d'être
     * commandé par le Fragment, ce qui corrige la faille de conception MVVM (🏗️).
     */
    // fun fetchMembers(...) a été supprimé.

    /**
     * JUSTIFICATION DE L'AJOUT : Une fonction d'extension est ajoutée pour convertir
     * le `Flow<Resource<List<User>>>` en `Flow<MembersUiState>`, afin de ne pas
     * dupliquer la logique de mapping.
     */
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