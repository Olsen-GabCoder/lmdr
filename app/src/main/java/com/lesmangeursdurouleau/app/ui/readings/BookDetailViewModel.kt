// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier BookDetailViewModel.kt
package com.lesmangeursdurouleau.app.ui.readings


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.domain.usecase.books.AddBookToLibraryUseCase
import com.lesmangeursdurouleau.app.domain.usecase.books.CheckBookInLibraryUseCase
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBookByIdUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// === DÉBUT DE LA MODIFICATION ===
/**
 * JUSTIFICATION: La classe d'état est enrichie avec `canBeRead`.
 * Cette propriété encapsule la logique métier (dans la bibliothèque ET a un lien de contenu)
 * directement dans l'état, ce qui simplifie grandement la logique d'affichage dans le Fragment.
 */
data class BookDetailUiState(
    val book: Book? = null,
    val isInLibrary: Boolean = false,
    val canBeRead: Boolean = false, // NOUVELLE PROPRIÉTÉ
    val isLoading: Boolean = true,
    val error: String? = null
)
// === FIN DE LA MODIFICATION ===

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val getBookByIdUseCase: GetBookByIdUseCase,
    private val addBookToLibraryUseCase: AddBookToLibraryUseCase,
    private val checkBookInLibraryUseCase: CheckBookInLibraryUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private val _addBookEvent = MutableSharedFlow<Resource<Unit>>()
    val addBookEvent: SharedFlow<Resource<Unit>> = _addBookEvent.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadBookDetails(bookId: String) {
        if (bookId.isBlank()) {
            _uiState.value = BookDetailUiState(isLoading = false, error = "ID du livre invalide.")
            return
        }

        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _uiState.value = BookDetailUiState(isLoading = false, error = "Utilisateur non authentifié.")
            return
        }

        viewModelScope.launch {
            getBookByIdUseCase(bookId)
                .flatMapLatest { bookResource ->
                    when (bookResource) {
                        is Resource.Success -> {
                            val book = bookResource.data
                            if (book != null) {
                                checkBookInLibraryUseCase(userId, book.id).map { libraryResource ->
                                    val isInLibrary = libraryResource.data ?: false
                                    // === DÉBUT DE LA MODIFICATION ===
                                    // La logique de `canBeRead` est calculée ici
                                    BookDetailUiState(
                                        book = book,
                                        isInLibrary = isInLibrary,
                                        canBeRead = isInLibrary && !book.contentUrl.isNullOrBlank(), // La condition est ici
                                        isLoading = false,
                                        error = libraryResource.message
                                    )
                                    // === FIN DE LA MODIFICATION ===
                                }
                            } else {
                                flowOf(BookDetailUiState(isLoading = false, error = "Livre non trouvé."))
                            }
                        }
                        is Resource.Error -> flowOf(BookDetailUiState(isLoading = false, error = bookResource.message))
                        is Resource.Loading -> flowOf(BookDetailUiState(isLoading = true))
                    }
                }
                .catch { e ->
                    Log.e("BookDetailViewModel", "Exception dans le flux de chargement", e)
                    emit(BookDetailUiState(isLoading = false, error = "Erreur technique: ${e.localizedMessage}"))
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun addBookToLibrary() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            viewModelScope.launch { _addBookEvent.emit(Resource.Error("Vous devez être connecté.")) }
            return
        }

        val currentBook = _uiState.value.book
        if (currentBook == null) {
            viewModelScope.launch { _addBookEvent.emit(Resource.Error("Détails du livre non disponibles.")) }
            return
        }

        viewModelScope.launch {
            _addBookEvent.emit(Resource.Loading())
            val result = addBookToLibraryUseCase(userId, currentBook)
            _addBookEvent.emit(result)
        }
    }
}