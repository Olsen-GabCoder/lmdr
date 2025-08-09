// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier AdminPanelViewModel.kt
package com.lesmangeursdurouleau.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.domain.usecase.admin.SetUserAdminUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminPanelViewModel @Inject constructor(
    private val setUserAdminUseCase: SetUserAdminUseCase
) : ViewModel() {

    // === DÉBUT DE LA MODIFICATION ===

    // JUSTIFICATION: Nous séparons l'état de l'UI (qui est persistant) des événements (qui sont à usage unique).
    // `isLoading` est un état : l'UI doit toujours savoir si une opération est en cours.
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // JUSTIFICATION: `_eventFlow` est un SharedFlow. Il est parfait pour diffuser des événements
    // qui ne doivent être consommés qu'une seule fois (Toast, navigation...).
    // Contrairement à un StateFlow, il ne retient pas la dernière valeur pour les nouveaux observateurs.
    private val _eventFlow = MutableSharedFlow<Resource<String>>()
    val eventFlow: SharedFlow<Resource<String>> = _eventFlow.asSharedFlow()

    // L'ancien StateFlow `_setUserRoleResult` est maintenant remplacé par `_isLoading` et `_eventFlow`.

    /**
     * Tente de promouvoir un utilisateur au rang d'administrateur.
     * @param email L'adresse e-mail de l'utilisateur cible.
     */
    fun onSetUserAsAdminClicked(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = setUserAdminUseCase(email)
            _eventFlow.emit(result) // On émet le résultat comme un événement.
            _isLoading.value = false
        }
    }

    // JUSTIFICATION: La méthode `consume...` devient inutile car le SharedFlow gère
    // nativement la non-persistance des événements. Le code est plus simple et plus sûr.
    /*
    fun consumeSetUserRoleResult() { ... }
    */

    // === FIN DE LA MODIFICATION ===
}