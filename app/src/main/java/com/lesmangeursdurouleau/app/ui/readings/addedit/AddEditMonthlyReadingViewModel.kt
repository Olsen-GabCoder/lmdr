// PRÊT À COLLER - Remplacez le contenu de votre fichier AddEditMonthlyReadingViewModel.kt
package com.lesmangeursdurouleau.app.ui.readings.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.PhaseStatus
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksUseCase
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
    private val getBooksUseCase: GetBooksUseCase,
    private val getMonthlyReadingByIdUseCase: GetMonthlyReadingByIdUseCase,
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
        monthlyReadingId?.let { loadMonthlyReadingForEdit(it) }
    }

    private fun loadAllBooks() {
        viewModelScope.launch {
            getBooksUseCase().collect { _allBooks.value = it }
        }
    }

    private fun loadMonthlyReadingForEdit(readingId: String) {
        viewModelScope.launch {
            val monthlyReadingFlow = getMonthlyReadingByIdUseCase(readingId)

            combine(monthlyReadingFlow, allBooks.filterIsInstance<Resource.Success<List<Book>>>()) { readingResource, booksResource ->
                when (readingResource) {
                    is Resource.Success -> {
                        val reading = readingResource.data
                        val book = reading?.let { r -> booksResource.data?.find { it.id == r.bookId } }
                        Resource.Success(Pair(reading, book))
                    }
                    is Resource.Error -> Resource.Error(readingResource.message.toString())
                    is Resource.Loading -> Resource.Loading()
                }
            }.collect { result ->
                _monthlyReadingAndBookForEdit.value = result
            }
        }
    }

    fun save(
        bookFromForm: Book,
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
                bookFromForm = bookFromForm,
                existingBookId = existingBookId
            )
            _saveResult.value = result
        }
    }
}