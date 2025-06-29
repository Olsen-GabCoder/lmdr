// app/src/main/java/com/lesmangeursdurouleau/app/ui/readings/detail/BookDetailViewModel.kt
package com.lesmangeursdurouleau.app.ui.readings

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.domain.usecase.books.AddBookToLibraryUseCase
import com.lesmangeursdurouleau.app.domain.usecase.books.CheckBookInLibraryUseCase
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBookByIdUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val getBookByIdUseCase: GetBookByIdUseCase,
    private val addBookToLibraryUseCase: AddBookToLibraryUseCase,
    private val checkBookInLibraryUseCase: CheckBookInLibraryUseCase,
    private val firebaseAuth: FirebaseAuth // Injecter FirebaseAuth pour obtenir l'UID
) : ViewModel() {

    private val _bookDetails = MutableLiveData<Resource<Book?>>()
    val bookDetails: LiveData<Resource<Book?>> = _bookDetails

    // LiveData pour l'état de présence du livre dans la bibliothèque
    private val _isBookInLibrary = MutableLiveData<Resource<Boolean>>()
    val isBookInLibrary: LiveData<Resource<Boolean>> = _isBookInLibrary

    // LiveData pour le résultat de l'ajout du livre
    private val _addBookToLibraryResult = MutableLiveData<Resource<Unit>>()
    val addBookToLibraryResult: LiveData<Resource<Unit>> = _addBookToLibraryResult

    private var checkInLibraryJob: Job? = null

    fun loadBookDetails(bookId: String) {
        if (bookId.isBlank()) {
            _bookDetails.value = Resource.Error("ID du livre invalide.")
            Log.w("BookDetailViewModel", "loadBookDetails called with blank bookId.")
            return
        }

        Log.d("BookDetailViewModel", "Loading details for book ID: $bookId")
        viewModelScope.launch {
            getBookByIdUseCase(bookId)
                .catch { e ->
                    Log.e("BookDetailViewModel", "Exception in book detail flow for ID $bookId", e)
                    _bookDetails.postValue(Resource.Error("Erreur technique: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    _bookDetails.value = resource
                    when(resource) {
                        is Resource.Success -> {
                            if (resource.data != null) {
                                Log.d("BookDetailViewModel", "Book ID $bookId loaded: ${resource.data.title}")
                                // Une fois le livre chargé, on vérifie s'il est dans la bibliothèque
                                checkIfBookIsInLibrary(resource.data.id)
                            } else {
                                Log.d("BookDetailViewModel", "Book ID $bookId not found or data is null.")
                            }
                        }
                        is Resource.Error -> Log.e("BookDetailViewModel", "Error loading book ID $bookId: ${resource.message}")
                        is Resource.Loading -> Log.d("BookDetailViewModel", "Loading book ID $bookId...")
                    }
                }
        }
    }

    private fun checkIfBookIsInLibrary(bookId: String) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _isBookInLibrary.value = Resource.Error("Utilisateur non authentifié.")
            return
        }

        // Annuler la coroutine précédente si elle existe pour éviter les écoutes multiples
        checkInLibraryJob?.cancel()
        checkInLibraryJob = viewModelScope.launch {
            checkBookInLibraryUseCase(userId, bookId)
                .catch { e ->
                    Log.e("BookDetailViewModel", "Exception checking if book is in library", e)
                    _isBookInLibrary.postValue(Resource.Error("Erreur technique: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    _isBookInLibrary.value = resource
                }
        }
    }

    fun addBookToLibrary() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _addBookToLibraryResult.value = Resource.Error("Vous devez être connecté pour ajouter un livre.")
            return
        }

        val book = (_bookDetails.value as? Resource.Success)?.data
        if (book == null) {
            _addBookToLibraryResult.value = Resource.Error("Les détails du livre n'ont pas pu être chargés.")
            return
        }

        // Indiquer que l'opération est en cours
        _addBookToLibraryResult.value = Resource.Loading()

        viewModelScope.launch {
            val result = addBookToLibraryUseCase(userId, book)
            _addBookToLibraryResult.value = result
        }
    }
}