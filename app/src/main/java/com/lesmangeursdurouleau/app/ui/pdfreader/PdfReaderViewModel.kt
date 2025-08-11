package com.lesmangeursdurouleau.app.ui.pdfreader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.repository.OfflineBookRepository
import com.lesmangeursdurouleau.app.domain.usecase.library.GetReadingProgressUseCase
import com.lesmangeursdurouleau.app.domain.usecase.library.SaveReadingProgressUseCase
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
    // === CORRECTION ===
    // RÉINTRODUCTION : Le contexte est nécessaire pour accéder au cache de l'application.
    @ApplicationContext private val context: Context,
    // === FIN DE LA CORRECTION ===
    private val offlineBookRepository: OfflineBookRepository,
    private val firebaseAuth: FirebaseAuth,
    private val getReadingProgressUseCase: GetReadingProgressUseCase,
    private val saveReadingProgressUseCase: SaveReadingProgressUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: String = savedStateHandle.get("bookId") ?: ""
    private val bookTitle: String? = savedStateHandle.get("bookTitle")
    private val pdfUrl: String = savedStateHandle.get("pdfUrl") ?: ""

    private val userId: String = firebaseAuth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(PdfReaderUiState(bookTitle = bookTitle))
    val uiState: StateFlow<PdfReaderUiState> get() = _uiState

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val initialPage = getReadingProgressUseCase(userId, bookId)
            _uiState.update { it.copy(initialPage = initialPage) }
            loadPdf()
        }
    }

    private fun loadPdf() {
        if (pdfUrl.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "L'URL du contenu PDF est manquante.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val file = withContext(Dispatchers.IO) {
                    val offlineFile = offlineBookRepository.getBookFile(bookId)
                    if (offlineFile != null) {
                        return@withContext offlineFile
                    }

                    // La variable 'context' est de nouveau disponible ici.
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

    fun saveCurrentPage(page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            saveReadingProgressUseCase(userId, bookId, page)
        }
    }
}