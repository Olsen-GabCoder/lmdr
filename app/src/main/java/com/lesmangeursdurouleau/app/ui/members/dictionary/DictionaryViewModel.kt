// PRÊT À COLLER - Fichier 100% complet
package com.lesmangeursdurouleau.app.ui.members.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.repository.DictionaryRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Lance la recherche d'un mot via le repository.
     * Met à jour l'état de l'UI pour refléter le chargement, le succès ou l'erreur.
     *
     * @param word Le mot à rechercher.
     */
    fun searchWord(word: String) {
        viewModelScope.launch {
            // Étape 1: Mettre l'UI en état de chargement et nettoyer les anciens résultats/erreurs
            _uiState.update {
                it.copy(
                    isLoading = true,
                    definition = null,
                    errorMessage = null
                )
            }

            // Étape 2: Appeler le repository
            val result = dictionaryRepository.getDefinition(word)

            // Étape 3: Mettre à jour l'UI avec le résultat
            when (result) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            definition = result.data
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
                is Resource.Loading -> { /* Cas déjà géré manuellement ci-dessus */ }
            }
        }
    }
}