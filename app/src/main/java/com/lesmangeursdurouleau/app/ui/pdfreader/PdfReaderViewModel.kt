// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PdfReaderViewModel.kt
package com.lesmangeursdurouleau.app.ui.pdfreader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import javax.inject.Inject

/**
 * L'état de l'UI est enrichi pour gérer le chargement et l'affichage du PDF.
 */
data class PdfReaderUiState(
    val bookTitle: String? = null,
    val pdfInputStream: InputStream? = null, // Contient le flux de données du PDF une fois téléchargé
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: String = savedStateHandle.get("bookId") ?: ""
    private val bookTitle: String? = savedStateHandle.get("bookTitle")
    private val pdfUrl: String = savedStateHandle.get("pdfUrl") ?: ""

    private val _uiState = MutableStateFlow(PdfReaderUiState(bookTitle = bookTitle))
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    init {
        loadPdf()
    }

    // === DÉBUT DE LA MODIFICATION ===
    private fun loadPdf() {
        if (pdfUrl.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "L'URL du contenu PDF est manquante.") }
            return
        }

        // On lance le téléchargement dans une coroutine
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Le téléchargement est une opération réseau, on utilise le Dispatcher.IO
                val inputStream = withContext(Dispatchers.IO) {
                    URL(pdfUrl).openStream()
                }
                // Si le téléchargement réussit, on met à jour l'état avec le flux de données
                _uiState.update { it.copy(isLoading = false, pdfInputStream = inputStream) }
            } catch (e: Exception) {
                // En cas d'erreur, on met à jour l'état avec le message d'erreur
                _uiState.update { it.copy(isLoading = false, error = "Échec du téléchargement du PDF: ${e.message}") }
            }
        }
    }
    // === FIN DE LA MODIFICATION ===
}