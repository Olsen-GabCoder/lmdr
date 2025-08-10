// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PdfReaderViewModel.kt
package com.lesmangeursdurouleau.app.ui.pdfreader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject

/**
 * MODIFIÉ: L'état de l'UI contient maintenant la page initiale à afficher,
 * qui sera chargée depuis les préférences.
 */
data class PdfReaderUiState(
    val bookTitle: String? = null,
    val pdfFile: File? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val initialPage: Int = 0 // NOUVEAU: La page sur laquelle le lecteur doit s'ouvrir.
)

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: String = savedStateHandle.get("bookId") ?: ""
    private val bookTitle: String? = savedStateHandle.get("bookTitle")
    private val pdfUrl: String = savedStateHandle.get("pdfUrl") ?: ""

    // === DÉBUT DE LA MODIFICATION ===

    // Préférences pour stocker la dernière page lue par livre.
    private val sharedPreferences = context.getSharedPreferences("PdfReaderPrefs", Context.MODE_PRIVATE)
    private val lastPageKey = "last_page_$bookId"

    private val _uiState: MutableStateFlow<PdfReaderUiState>

    init {
        // On lit la dernière page sauvegardée AVANT toute chose.
        val lastSavedPage = sharedPreferences.getInt(lastPageKey, 0)
        _uiState = MutableStateFlow(PdfReaderUiState(bookTitle = bookTitle, initialPage = lastSavedPage))
        loadPdf()
    }
    // === FIN DE LA MODIFICATION ===

    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()


    private fun loadPdf() {
        if (pdfUrl.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "L'URL du contenu PDF est manquante.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val file = withContext(Dispatchers.IO) {
                    val cacheFile = File(context.cacheDir, "book_cache_$bookId.pdf")
                    if (cacheFile.exists()) {
                        return@withContext cacheFile
                    }
                    URL(pdfUrl).openStream().use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    cacheFile
                }
                _uiState.update { it.copy(isLoading = false, pdfFile = file) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Échec du chargement du PDF: ${e.message}") }
            }
        }
    }

    // === DÉBUT DE LA MODIFICATION ===
    /**
     * NOUVEAU: Sauvegarde la page actuelle pour le livre en cours dans les SharedPreferences.
     * Cette fonction est conçue pour être appelée depuis le Fragment.
     */
    fun saveCurrentPage(page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            sharedPreferences.edit().putInt(lastPageKey, page).apply()
        }
    }
    // === FIN DE LA MODIFICATION ===
}