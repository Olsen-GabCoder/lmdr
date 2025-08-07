// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier AddEditMonthlyReadingViewModel.kt
package com.lesmangeursdurouleau.app.ui.readings.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.PhaseStatus
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBookByIdUseCase
import com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings.GetMonthlyReadingByIdUseCase
import com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings.SaveMonthlyReadingUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class AddEditMonthlyReadingViewModel @Inject constructor(
    private val getBookByIdUseCase: GetBookByIdUseCase,
    private val getMonthlyReadingByIdUseCase: GetMonthlyReadingByIdUseCase,
    private val saveMonthlyReadingUseCase: SaveMonthlyReadingUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val monthlyReadingId: String? = savedStateHandle.get("monthlyReadingId")
    private val newBookId: String? = savedStateHandle.get("bookId")

    private val _uiState = MutableStateFlow<Resource<Pair<MonthlyReading?, Book?>>>(Resource.Loading())
    val uiState: StateFlow<Resource<Pair<MonthlyReading?, Book?>>> = _uiState.asStateFlow()

    private val _saveResult = MutableStateFlow<Resource<Unit>?>(null)
    val saveResult: StateFlow<Resource<Unit>?> = _saveResult.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = Resource.Loading()
            if (monthlyReadingId != null) {
                // Cas 1 : On MODIFIE une lecture existante.
                // JUSTIFICATION DE LA CORRECTION : Nous utilisons `collect` pour nous assurer de traiter
                // tous les états du Flow, et pas seulement le premier (`Resource.Loading`).
                getMonthlyReadingByIdUseCase(monthlyReadingId).collect { readingResource ->
                    when (readingResource) {
                        is Resource.Success -> {
                            val reading = readingResource.data
                            if (reading?.bookId != null) {
                                // Une fois la lecture obtenue, on récupère le livre associé.
                                getBookByIdUseCase(reading.bookId).collect { bookResource ->
                                    if (bookResource !is Resource.Loading) {
                                        _uiState.value = Resource.Success(Pair(reading, bookResource.data))
                                    }
                                }
                            } else {
                                _uiState.value = Resource.Error("Lecture trouvée mais sans ID de livre associé.")
                            }
                        }
                        is Resource.Error -> {
                            _uiState.value = Resource.Error(readingResource.message ?: "Lecture non trouvée")
                        }
                        is Resource.Loading -> {
                            // On reste en état de chargement global.
                        }
                    }
                }
            } else if (newBookId != null) {
                // Cas 2 : On CRÉE une nouvelle lecture pour un livre existant.
                getBookByIdUseCase(newBookId).collect { bookResource ->
                    if (bookResource !is Resource.Loading) {
                        _uiState.value = when (bookResource) {
                            is Resource.Success -> Resource.Success(Pair(null, bookResource.data))
                            is Resource.Error -> Resource.Error(bookResource.message ?: "Impossible de charger le livre")
                            is Resource.Loading -> Resource.Loading() // Théoriquement inatteignable
                        }
                    }
                }
            } else {
                _uiState.value = Resource.Error("Aucun identifiant de livre ou de lecture fourni.")
            }
        }
    }

    fun save(
        book: Book,
        year: Int,
        month: Int,
        analysisDate: Date,
        analysisStatus: PhaseStatus,
        debateDate: Date,
        debateStatus: PhaseStatus,
        customDescription: String?
    ) {
        viewModelScope.launch {
            _saveResult.value = Resource.Loading()
            val result = saveMonthlyReadingUseCase(
                monthlyReadingId = monthlyReadingId,
                year = year,
                month = month,
                analysisDate = analysisDate,
                analysisStatus = analysisStatus,
                analysisMeetingLink = null,
                debateDate = debateDate,
                debateStatus = debateStatus,
                debateMeetingLink = null,
                customDescription = customDescription,
                bookFromForm = book,
                existingBookId = book.id
            )
            _saveResult.value = result
        }
    }

    fun consumeSaveResult() {
        _saveResult.value = null
    }
}