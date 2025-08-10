// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier BookDetailViewModel.kt
package com.lesmangeursdurouleau.app.ui.readings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.repository.OfflineBookRepository
import com.lesmangeursdurouleau.app.domain.usecase.books.AddBookToLibraryUseCase
import com.lesmangeursdurouleau.app.domain.usecase.books.CheckBookInLibraryUseCase
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBookByIdUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookDetailUiState(
    val book: Book? = null,
    val isInLibrary: Boolean = false,
    val canBeRead: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModel @Inject constructor(
    private val getBookByIdUseCase: GetBookByIdUseCase,
    private val addBookToLibraryUseCase: AddBookToLibraryUseCase,
    private val checkBookInLibraryUseCase: CheckBookInLibraryUseCase,
    private val offlineBookRepository: OfflineBookRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private val _addBookEvent = MutableSharedFlow<Resource<Unit>>()
    val addBookEvent: SharedFlow<Resource<Unit>> = _addBookEvent.asSharedFlow()

    private var bookDetailsJob: Job? = null

    // === DÉBUT DE LA MODIFICATION ===
    // La fonction est de nouveau publique pour être appelée par le Fragment.
    fun loadBookDetails(bookId: String) {
        // === FIN DE LA MODIFICATION ===
        bookDetailsJob?.cancel()

        if (bookId.isBlank()) {
            _uiState.value = BookDetailUiState(isLoading = false, error = "ID du livre invalide.")
            return
        }
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _uiState.value = BookDetailUiState(isLoading = false, error = "Utilisateur non authentifié.")
            return
        }

        bookDetailsJob = viewModelScope.launch {
            val bookFlow = getBookByIdUseCase(bookId)
            val libraryFlow = checkBookInLibraryUseCase(userId, bookId)
            val downloadFlow = offlineBookRepository.isBookDownloaded(bookId)

            combine(bookFlow, libraryFlow, downloadFlow) { bookResource, libraryResource, isDownloaded ->
                when (bookResource) {
                    is Resource.Loading -> _uiState.value.copy(isLoading = true)
                    is Resource.Error -> _uiState.value.copy(isLoading = false, error = bookResource.message)
                    is Resource.Success -> {
                        val book = bookResource.data
                        val isInLibrary = libraryResource.data ?: false
                        if (book != null) {
                            _uiState.value.copy(
                                book = book,
                                isInLibrary = isInLibrary,
                                canBeRead = isInLibrary && !book.contentUrl.isNullOrBlank(),
                                isLoading = false,
                                error = libraryResource.message,
                                isDownloaded = isDownloaded
                            )
                        } else {
                            _uiState.value.copy(isLoading = false, error = "Livre non trouvé.")
                        }
                    }
                }
            }.catch { e ->
                Log.e("BookDetailViewModel", "Exception dans le flux combiné", e)
                emit(_uiState.value.copy(isLoading = false, error = "Erreur technique: ${e.localizedMessage}"))
            }.collect { state ->
                _uiState.update { it.copy(
                    book = state.book,
                    isInLibrary = state.isInLibrary,
                    canBeRead = state.canBeRead,
                    isLoading = state.isLoading,
                    error = state.error,
                    isDownloaded = state.isDownloaded
                )}
            }
        }
    }

    fun downloadBook() {
        val book = _uiState.value.book ?: return
        val url = book.contentUrl ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true) }
            val result = offlineBookRepository.downloadBook(book.id, url)
            if (result is Resource.Error) {
                _uiState.update { it.copy(error = result.message) }
            }
            _uiState.update { it.copy(isDownloading = false) }
        }
    }

    fun deleteDownloadedBook() {
        val bookId = _uiState.value.book?.id ?: return
        viewModelScope.launch {
            offlineBookRepository.deleteBook(bookId)
        }
    }

    fun addBookToLibrary() {
        val userId = firebaseAuth.currentUser?.uid ?: return
        val currentBook = _uiState.value.book ?: return

        viewModelScope.launch {
            _addBookEvent.emit(Resource.Loading())
            val result = addBookToLibraryUseCase(userId, currentBook)
            _addBookEvent.emit(result)
        }
    }
}