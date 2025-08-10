// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PdfReaderViewModel.kt
package com.lesmangeursdurouleau.app.ui.pdfreader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.repository.OfflineBookRepository
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

data class PdfReaderUiState(
    val bookTitle: String? = null,
    val pdfFile: File? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val initialPage: Int = 0
)

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    // === DÉBUT DE LA MODIFICATION ===
    // NOUVEAU: Injection de notre gestionnaire de fichiers hors-ligne.
    private val offlineBookRepository: OfflineBookRepository,
    // === FIN DE LA MODIFICATION ===
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: String = savedStateHandle.get("bookId") ?: ""
    private val bookTitle: String? = savedStateHandle.get("bookTitle")
    private val pdfUrl: String = savedStateHandle.get("pdfUrl") ?: ""

    private val sharedPreferences = context.getSharedPreferences("PdfReaderPrefs", Context.MODE_PRIVATE)
    private val lastPageKey = "last_page_$bookId"

    private val _uiState: MutableStateFlow<PdfReaderUiState>
    val uiState: StateFlow<PdfReaderUiState> get() = _uiState

    init {
        val lastSavedPage = sharedPreferences.getInt(lastPageKey, 0)
        _uiState = MutableStateFlow(PdfReaderUiState(bookTitle = bookTitle, initialPage = lastSavedPage))
        loadPdf()
    }

    // === DÉBUT DE LA MODIFICATION ===
    /**
     * MODIFIÉ: La logique de chargement suit maintenant une hiérarchie claire :
     * 1. Vérifie si le livre existe dans le stockage permanent (hors-ligne).
     * 2. Sinon, vérifie s'il existe dans le cache temporaire.
     * 3. Sinon, le télécharge depuis le réseau et le place dans le cache.
     */
    private fun loadPdf() {
        if (pdfUrl.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "L'URL du contenu PDF est manquante.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val file = withContext(Dispatchers.IO) {
                    // 1. Chercher dans le stockage permanent (hors-ligne)
                    val offlineFile = offlineBookRepository.getBookFile(bookId)
                    if (offlineFile != null) {
                        return@withContext offlineFile
                    }

                    // 2. Chercher dans le cache temporaire
                    val cacheFile = File(context.cacheDir, "book_cache_$bookId.pdf")
                    if (cacheFile.exists()) {
                        return@withContext cacheFile
                    }

                    // 3. Télécharger depuis le réseau vers le cache
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
    // === FIN DE LA MODIFICATION ===

    fun saveCurrentPage(page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            sharedPreferences.edit().putInt(lastPageKey, page).apply()
        }
    }
}