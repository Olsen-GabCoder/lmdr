// PRÊT À COLLER - Créez un nouveau fichier ManageBookViewModel.kt dans un nouveau package (ex: ui/admin/managebook)
package com.lesmangeursdurouleau.app.ui.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.domain.usecase.books.CreateBookWithFilesUseCase
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État de l'interface utilisateur pour l'écran de gestion de livre.
 */
data class ManageBookUiState(
    val bookList: Resource<List<Book>> = Resource.Loading(),
    val saveResult: Resource<String>? = null
)

@HiltViewModel
class ManageBookViewModel @Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val createBookWithFilesUseCase: CreateBookWithFilesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageBookUiState())
    val uiState: StateFlow<ManageBookUiState> = _uiState.asStateFlow()

    init {
        loadBooks()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            getBooksUseCase().collect { bookResource ->
                _uiState.value = _uiState.value.copy(bookList = bookResource)
            }
        }
    }

    /**
     * Lance la création d'un nouveau livre avec ses fichiers associés.
     */
    fun createBook(
        title: String,
        author: String,
        synopsis: String,
        totalPages: Int,
        coverImage: ByteArray?,
        pdfUri: Uri?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saveResult = Resource.Loading())

            // Validation simple
            if (title.isBlank() || author.isBlank()) {
                _uiState.value = _uiState.value.copy(saveResult = Resource.Error("Le titre et l'auteur sont obligatoires."))
                return@launch
            }

            val newBook = Book(
                title = title,
                author = author,
                synopsis = synopsis.takeIf { it.isNotBlank() },
                totalPages = totalPages
            )

            val result = createBookWithFilesUseCase(newBook, coverImage, pdfUri)
            _uiState.value = _uiState.value.copy(saveResult = result)
        }
    }

    /**
     * Réinitialise l'état du résultat de la sauvegarde pour l'UI.
     */
    fun consumeSaveResult() {
        _uiState.value = _uiState.value.copy(saveResult = null)
    }
}