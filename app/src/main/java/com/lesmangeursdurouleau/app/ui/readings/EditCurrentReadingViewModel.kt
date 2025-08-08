package com.lesmangeursdurouleau.app.ui.readings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.ReadingStatus
import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.data.repository.BookRepository
// CORRECTION : Importation du UseCase correct (au singulier).
import com.lesmangeursdurouleau.app.domain.usecase.library.GetLibraryEntryUseCase
import com.lesmangeursdurouleau.app.domain.usecase.library.UpdateLibraryEntryUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class EditReadingEvent {
    data class ShowToast(val message: String) : EditReadingEvent()
    data object NavigateBack : EditReadingEvent()
    data object ShowDeleteConfirmationDialog : EditReadingEvent()
}

data class EditReadingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val libraryEntry: UserLibraryEntry? = null,
    val bookDetails: Book? = null,
    val selectedBook: Book? = null
)

@HiltViewModel
class EditCurrentReadingViewModel @Inject constructor(
    private val bookRepository: BookRepository, // Conservé pour la suppression
    // CORRECTION : Injection du UseCase correct (au singulier) pour récupérer une seule entrée.
    private val getLibraryEntryUseCase: GetLibraryEntryUseCase,
    private val updateLibraryEntryUseCase: UpdateLibraryEntryUseCase,
    private val firebaseAuth: FirebaseAuth,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookIdFromNav: String? = savedStateHandle.get("bookId")

    private val _uiState = MutableStateFlow(EditReadingUiState(isLoading = true))
    val uiState: StateFlow<EditReadingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditReadingEvent>()
    val events: SharedFlow<EditReadingEvent> = _events.asSharedFlow()

    private val currentUserId: String? = firebaseAuth.currentUser?.uid

    init {
        loadReadingEntry()
    }

    private fun loadReadingEntry() {
        val userId = currentUserId
        val bookId = bookIdFromNav

        if (userId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Utilisateur non connecté.") }
            return
        }

        if (bookId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, error = null) }
            return
        }

        viewModelScope.launch {
            // CORRECTION : Appel du UseCase correct, qui prend bien userId et bookId.
            // Il retourne bien un Flow<Resource<UserLibraryEntry?>>, ce qui correspond au reste du code.
            getLibraryEntryUseCase(userId, bookId)
                .flatMapLatest { entryResource ->
                    when (entryResource) {
                        is Resource.Success -> {
                            // La logique ici est maintenant correcte car entryResource.data est bien un UserLibraryEntry?
                            val entry = entryResource.data
                            val bookIdToFetch = entry?.bookId ?: bookId
                            bookRepository.getBookById(bookIdToFetch).map { bookResource ->
                                Pair(entryResource, bookResource)
                            }
                        }
                        is Resource.Loading -> flowOf(Pair(entryResource, Resource.Loading<Book?>()))
                        is Resource.Error -> flowOf(Pair(entryResource,
                            entryResource.message?.let { Resource.Error<Book?>(it) }))
                    }
                }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Une erreur inattendue est survenue."
                        )
                    }
                }
                .collect { (entryResource, bookResource) ->
                    _uiState.update { currentState ->
                        val newError = (entryResource as? Resource.Error)?.message
                            ?: (bookResource as? Resource.Error)?.message

                        currentState.copy(
                            isLoading = entryResource is Resource.Loading || bookResource is Resource.Loading,
                            libraryEntry = (entryResource as? Resource.Success)?.data,
                            bookDetails = (bookResource as? Resource.Success)?.data,
                            error = newError
                        )
                    }
                }
        }
    }

    fun setSelectedBook(book: Book?) {
        _uiState.update {
            it.copy(
                selectedBook = book,
                libraryEntry = null,
                bookDetails = book
            )
        }
    }

    fun saveReadingEntry(currentPageStr: String, totalPagesStr: String) {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            sendEvent(EditReadingEvent.ShowToast("Utilisateur non connecté."))
            return
        }

        val currentState = _uiState.value
        val bookForEntry = currentState.selectedBook ?: currentState.bookDetails
        if (bookForEntry == null || bookForEntry.id.isBlank()) {
            sendEvent(EditReadingEvent.ShowToast("Aucun livre sélectionné ou ID de livre invalide."))
            return
        }

        val currentPage = currentPageStr.toIntOrNull() ?: currentState.libraryEntry?.currentPage ?: 0

        // AMÉLIORATION : Logique de totalPages plus robuste.
        // Priorité : 1. Nouvelle valeur entrée | 2. Ancienne valeur de l'entrée | 3. Valeur par défaut du livre.
        val totalPages = totalPagesStr.toIntOrNull()
            ?: currentState.libraryEntry?.totalPages?.takeIf { it > 0 }
            ?: bookForEntry.totalPages

        if (totalPages <= 0 || currentPage < 0 || currentPage > totalPages) {
            sendEvent(EditReadingEvent.ShowToast("Les numéros de pages sont invalides."))
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val existingEntry = currentState.libraryEntry
            val newStatus = when {
                currentPage >= totalPages -> ReadingStatus.FINISHED
                currentPage > 0 -> ReadingStatus.READING
                else -> ReadingStatus.TO_READ
            }

            val entryToSave = (existingEntry ?: UserLibraryEntry(
                bookId = bookForEntry.id,
                userId = userId
            )).copy(
                currentPage = currentPage,
                totalPages = totalPages,
                status = newStatus,
                lastReadDate = Timestamp.now()
            )

            val result = updateLibraryEntryUseCase(userId, entryToSave)

            _uiState.update { it.copy(isLoading = false) }
            when (result) {
                is Resource.Success -> {
                    sendEvent(EditReadingEvent.ShowToast("Progression enregistrée !"))
                    sendEvent(EditReadingEvent.NavigateBack)
                }
                is Resource.Error -> sendEvent(EditReadingEvent.ShowToast(result.message ?: "Erreur inconnue"))
                is Resource.Loading -> { /* No-op */ }
            }
        }
    }

    fun confirmRemoveReading() {
        sendEvent(EditReadingEvent.ShowDeleteConfirmationDialog)
    }



    fun removeReadingEntry() {
        val userId = currentUserId
        if (userId.isNullOrBlank()) {
            sendEvent(EditReadingEvent.ShowToast("Utilisateur non connecté."))
            return
        }

        val bookToRemoveId = _uiState.value.selectedBook?.id ?: _uiState.value.bookDetails?.id

        if (bookToRemoveId.isNullOrBlank()) {
            sendEvent(EditReadingEvent.ShowToast("ID du livre invalide."))
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = bookRepository.removeBookFromUserLibrary(userId, bookToRemoveId)
            _uiState.update { it.copy(isLoading = false) }

            when (result) {
                is Resource.Success -> {
                    sendEvent(EditReadingEvent.ShowToast("Livre retiré de la bibliothèque."))
                    sendEvent(EditReadingEvent.NavigateBack)
                }
                is Resource.Error -> sendEvent(EditReadingEvent.ShowToast(result.message ?: "Erreur inconnue"))
                is Resource.Loading -> { /* No-op */ }
            }
        }
    }

    private fun sendEvent(event: EditReadingEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}