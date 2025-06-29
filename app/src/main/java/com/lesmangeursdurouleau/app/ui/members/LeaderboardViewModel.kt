// PRÊT À COLLER - Fichier complet
package com.lesmangeursdurouleau.app.ui.members // ou com.lesmangeursdurouleau.app.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.repository.LeaderboardRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel pour l'écran du classement d'affinité.
 * Il récupère la liste des meilleurs binômes depuis le LeaderboardRepository.
 */
@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository
) : ViewModel() {

    companion object {
        // Le nombre d'entrées que nous voulons afficher dans le classement.
        private const val LEADERBOARD_LIMIT = 20L
    }

    /**
     * Un StateFlow qui expose la liste des entrées du classement.
     * Le Fragment observera ce Flow pour mettre à jour l'UI.
     * Le classement est récupéré en temps réel et mis en cache pour 5 secondes
     * lorsque l'UI est visible.
     */
    val leaderboardState: StateFlow<Resource<List<Conversation>>> =
        leaderboardRepository.getAffinityLeaderboard(LEADERBOARD_LIMIT)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )
}