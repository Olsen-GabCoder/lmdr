// PRÊT À COLLER - Créez un nouveau fichier PdfReaderViewModel.kt dans un nouveau package (ex: ui/pdfreader)
package com.lesmangeursdurouleau.app.ui.pdfreader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * JUSTIFICATION: C'est l'état de l'UI pour notre nouvel écran de lecture.
 * Pour l'instant, il ne contient que les informations de base nécessaires
 * à l'affichage initial (comme le titre dans la barre d'outils).
 * Il sera enrichi plus tard avec l'état de chargement du PDF, la page actuelle, etc.
 */
data class PdfReaderUiState(
    val bookTitle: String? = null,
    val pdfUrl: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle // Permet de récupérer les arguments de navigation
) : ViewModel() {

    private val bookId: String = savedStateHandle.get("bookId") ?: ""
    private val bookTitle: String? = savedStateHandle.get("bookTitle")
    private val pdfUrl: String = savedStateHandle.get("pdfUrl") ?: ""

    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    init {
        if (pdfUrl.isBlank()) {
            _uiState.value = PdfReaderUiState(
                bookTitle = bookTitle,
                isLoading = false,
                error = "L'URL du contenu PDF est manquante."
            )
        } else {
            _uiState.value = PdfReaderUiState(
                bookTitle = bookTitle,
                pdfUrl = pdfUrl
            )
            // C'est ici que nous lancerons le chargement du PDF plus tard.
        }
    }

    // Les fonctions pour charger le PDF, mettre à jour la progression, etc., seront ajoutées ici.
}