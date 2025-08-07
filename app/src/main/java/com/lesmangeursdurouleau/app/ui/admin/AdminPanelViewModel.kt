// PRÊT À COLLER - Créez un nouveau fichier AdminPanelViewModel.kt (par ex. dans le package ui/admin)
package com.lesmangeursdurouleau.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.domain.usecase.admin.SetUserAdminUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminPanelViewModel @Inject constructor(
    private val setUserAdminUseCase: SetUserAdminUseCase
) : ViewModel() {

    // Ce StateFlow exposera le résultat de l'opération de promotion.
    private val _setUserRoleResult = MutableStateFlow<Resource<String>?>(null)
    val setUserRoleResult: StateFlow<Resource<String>?> = _setUserRoleResult.asStateFlow()

    /**
     * Tente de promouvoir un utilisateur au rang d'administrateur.
     * @param email L'adresse e-mail de l'utilisateur cible.
     */
    fun onSetUserAsAdminClicked(email: String) {
        viewModelScope.launch {
            _setUserRoleResult.value = Resource.Loading()
            val result = setUserAdminUseCase(email)
            _setUserRoleResult.value = result
        }
    }

    /**
     * Permet à l'UI de réinitialiser l'état du résultat une fois le message affiché,
     * pour éviter de le réafficher lors d'un changement de configuration.
     */
    fun consumeSetUserRoleResult() {
        _setUserRoleResult.value = null
    }
}