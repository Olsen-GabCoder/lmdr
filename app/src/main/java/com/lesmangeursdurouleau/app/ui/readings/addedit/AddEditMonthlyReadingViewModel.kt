// PRÊT À COLLER - Remplacez tout le contenu de votre fichier AddEditMonthlyReadingViewModel.kt par ceci.
package com.lesmangeursdurouleau.app.ui.readings.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.PhaseStatus
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksUseCase
import com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings.GetMonthlyReadingsUseCase
import com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings.SaveMonthlyReadingUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class AddEditMonthlyReadingViewModel @Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val getMonthlyReadingsUseCase: GetMonthlyReadingsUseCase,
    private val saveMonthlyReadingUseCase: SaveMonthlyReadingUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val monthlyReadingId: String? = savedStateHandle.get("monthlyReadingId")

    private val _allBooks = MutableStateFlow<Resource<List<Book>>>(Resource.Loading())
    val allBooks: StateFlow<Resource<List<Book>>> = _allBooks.asStateFlow()

    private val _monthlyReadingAndBookForEdit = MutableStateFlow<Resource<Pair<MonthlyReading?, Book?>>>(Resource.Loading())
    val monthlyReadingAndBookForEdit: StateFlow<Resource<Pair<MonthlyReading?, Book?>>> = _monthlyReadingAndBookForEdit.asStateFlow()

    private val _saveResult = MutableStateFlow<Resource<Unit>?>(null)
    val saveResult: StateFlow<Resource<Unit>?> = _saveResult.asStateFlow()

    init {
        loadAllBooks()
        monthlyReadingId?.let { loadMonthlyReading(it) }
    }

    private fun loadAllBooks() {
        viewModelScope.launch {
            getBooksUseCase().collect { _allBooks.value = it }
        }
    }

    private fun loadMonthlyReading(readingId: String) {
        viewModelScope.launch {
            val monthlyReadingFlow = getMonthlyReadingsUseCase(readingId)

            // CORRIGÉ: Le bloc combine renvoie maintenant toujours le bon type d'objet Resource.
            combine(monthlyReadingFlow, allBooks.filter { it is Resource.Success }) { readingResource, booksResource ->
                when {
                    readingResource is Resource.Success && booksResource is Resource.Success -> {
                        val reading = readingResource.data
                        val book = reading?.let { r -> booksResource.data?.find { it.id == r.bookId } }
                        Resource.Success(Pair(reading, book))
                    }
                    readingResource is Resource.Error -> {
                        // On propage l'erreur de lecture.
                        Resource.Error<Pair<MonthlyReading?, Book?>>(readingResource.message ?: "Erreur de chargement de la lecture.")
                    }
                    booksResource is Resource.Error -> {
                        // On propage l'erreur des livres.
                        Resource.Error<Pair<MonthlyReading?, Book?>>(booksResource.message ?: "Erreur de chargement des livres.")
                    }
                    else -> {
                        // Le cas par défaut est maintenant un Resource.Loading, et non plus un AuthResultWrapper.
                        Resource.Loading<Pair<MonthlyReading?, Book?>>()
                    }
                }
            }.collect {
                // Le cast forcé n'est plus nécessaire car le type est maintenant correct.
                _monthlyReadingAndBookForEdit.value = it
            }
        }
    }

    fun save(
        book: Book,
        year: Int,
        month: Int,
        analysisDate: Date,
        analysisStatus: PhaseStatus,
        analysisLink: String?,
        debateDate: Date,
        debateStatus: PhaseStatus,
        debateLink: String?,
        customDescription: String?,
        existingBookId: String?
    ) {
        viewModelScope.launch {
            _saveResult.value = Resource.Loading()
            val result = saveMonthlyReadingUseCase(
                monthlyReadingId = monthlyReadingId,
                year = year,
                month = month,
                analysisDate = analysisDate,
                analysisStatus = analysisStatus,
                analysisMeetingLink = analysisLink,
                debateDate = debateDate,
                debateStatus = debateStatus,
                debateMeetingLink = debateLink,
                customDescription = customDescription,
                book = book,
                existingBookId = existingBookId
            )
            _saveResult.value = result
        }
    }
}