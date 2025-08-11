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

    fun loadBookDetails(bookId: String) {
        bookDetailsJob?.cancel()

        if (bookId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "ID du livre invalide.") }
            return
        }
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, error = "Utilisateur non authentifié.") }
            return
        }

        bookDetailsJob = viewModelScope.launch {
            // === DÉBUT DE LA MODIFICATION ===
            // 1. On lance une tâche pour récupérer l'état de téléchargement initial.
            //    `.first()` prend la première valeur du flow puis l'annule, évitant les fuites.
            launch {
                try {
                    val isInitiallyDownloaded = offlineBookRepository.isBookDownloaded(bookId).first()
                    _uiState.update { it.copy(isDownloaded = isInitiallyDownloaded) }
                } catch (e: Exception) {
                    // Gérer l'erreur si la vérification échoue
                    Log.e("BookDetailViewModel", "Erreur de vérification du téléchargement", e)
                }
            }

            // 2. Le `combine` est simplifié car il n'a plus besoin d'observer le statut de téléchargement.
            val bookFlow = getBookByIdUseCase(bookId)
            val libraryFlow = checkBookInLibraryUseCase(userId, bookId)

            combine(bookFlow, libraryFlow) { bookResource, libraryResource ->
                when (bookResource) {
                    is Resource.Loading -> _uiState.value.copy(isLoading = true, error = null)
                    is Resource.Error -> _uiState.value.copy(isLoading = false, error = bookResource.message, book = null)
                    is Resource.Success -> {
                        val book = bookResource.data
                        val isInLibrary = libraryResource.data ?: false
                        if (book != null) {
                            _uiState.value.copy(
                                book = book,
                                isInLibrary = isInLibrary,
                                canBeRead = isInLibrary && !book.contentUrl.isNullOrBlank(),
                                isLoading = false,
                                error = if (libraryResource is Resource.Error) libraryResource.message else null
                                // Note : 'isDownloaded' n'est plus géré ici.
                            )
                        } else {
                            _uiState.value.copy(isLoading = false, error = "Livre non trouvé.", book = null)
                        }
                    }
                }
            }.catch { e ->
                Log.e("BookDetailViewModel", "Exception dans le flux combiné", e)
                emit(_uiState.value.copy(isLoading = false, error = "Erreur technique: ${e.localizedMessage}", book = null))
            }.collect { updatedState ->
                _uiState.update { currentState ->
                    // On fusionne le nouvel état (livre, librairie) avec l'état actuel (isDownloaded)
                    // pour ne pas écraser la valeur de 'isDownloaded'.
                    updatedState.copy(isDownloaded = currentState.isDownloaded)
                }
            }
            // === FIN DE LA MODIFICATION ===
        }
    }

    fun downloadBook() {
        val book = _uiState.value.book ?: return
        val url = book.contentUrl ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true) }
            val result = offlineBookRepository.downloadBook(book.id, url)

            // === DÉBUT DE LA MODIFICATION ===
            // Le ViewModel met à jour son propre état pour refléter le changement.
            val newDownloadState = result is Resource.Success
            _uiState.update { it.copy(
                isDownloading = false,
                isDownloaded = newDownloadState,
                error = if (result is Resource.Error) result.message else it.error
            )}
            // === FIN DE LA MODIFICATION ===
        }
    }

    fun deleteDownloadedBook() {
        val bookId = _uiState.value.book?.id ?: return
        viewModelScope.launch {
            offlineBookRepository.deleteBook(bookId)
            // === DÉBUT DE LA MODIFICATION ===
            // Le ViewModel met à jour son propre état pour refléter le changement.
            _uiState.update { it.copy(isDownloaded = false) }
            // === FIN DE LA MODIFICATION ===
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