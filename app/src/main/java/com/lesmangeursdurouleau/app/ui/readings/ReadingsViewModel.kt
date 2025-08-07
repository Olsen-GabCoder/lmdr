// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ReadingsViewModel.kt
package com.lesmangeursdurouleau.app.ui.readings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.PhaseStatus
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksUseCase
import com.lesmangeursdurouleau.app.ui.readings.adapter.MonthlyReadingWithBook
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

enum class ReadingsFilter {
    ALL,
    IN_PROGRESS,
    PLANNED,
    PAST
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingsViewModel @Inject constructor(
    private val monthlyReadingRepository: MonthlyReadingRepository,
    private val getBooksUseCase: GetBooksUseCase
    // JUSTIFICATION DE LA SUPPRESSION : La dépendance à AuthRepository a été retirée.
    // La responsabilité de vérifier les permissions d'administration n'appartient plus à ce ViewModel,
    // car l'interface qu'il sert est désormais purement consultative.
) : ViewModel() {

    private companion object {
        private const val TAG = "ReadingsViewModel"
    }

    private val _currentMonthYear = MutableStateFlow(Calendar.getInstance())
    val currentMonthYear: StateFlow<Calendar> = _currentMonthYear.asStateFlow()

    private val _currentFilter = MutableStateFlow(ReadingsFilter.ALL)
    val currentFilter: StateFlow<ReadingsFilter> = _currentFilter.asStateFlow()

    private val _booksMap = MutableStateFlow<Map<String, Book>>(emptyMap())

    private val _monthlyReadingsWithBooks = MutableStateFlow<Resource<List<MonthlyReadingWithBook>>>(Resource.Loading())
    val monthlyReadingsWithBooks: StateFlow<Resource<List<MonthlyReadingWithBook>>> = _monthlyReadingsWithBooks.asStateFlow()

    // JUSTIFICATION DE LA SUPPRESSION : Le StateFlow `canEditReadings` est retiré.
    // Le contrôle de la visibilité des outils d'administration n'est plus la responsabilité
    // de cet écran.

    init {
        loadAllBooksIntoMap()
        setupMonthlyReadingsFlow()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupMonthlyReadingsFlow() {
        viewModelScope.launch {
            val minuteTicker = flow {
                while (true) {
                    emit(Unit)
                    delay(60_000L)
                }
            }

            combine(
                _currentMonthYear,
                _currentFilter,
                minuteTicker
            ) { calendar, filter, _ ->
                Pair(calendar, filter)
            }
                .flatMapLatest<Pair<Calendar, ReadingsFilter>, Resource<List<MonthlyReadingWithBook>>> { (calendar, filter) ->
                    val rawReadingsFlow = if (filter == ReadingsFilter.ALL) {
                        monthlyReadingRepository.getMonthlyReadings(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH) + 1
                        )
                    } else {
                        monthlyReadingRepository.getAllMonthlyReadings()
                    }

                    rawReadingsFlow.combine<Resource<List<MonthlyReading>>, Map<String, Book>, Resource<List<MonthlyReadingWithBook>>>(
                        _booksMap
                    ) { readingsResource, booksMap ->
                        when (readingsResource) {
                            is Resource.Loading -> Resource.Loading()
                            is Resource.Error -> Resource.Error(readingsResource.message ?: "Erreur de chargement.")
                            is Resource.Success -> {
                                val now = Date()
                                val combinedList = readingsResource.data
                                    ?.map { monthlyReading ->
                                        MonthlyReadingWithBook(monthlyReading, booksMap[monthlyReading.bookId])
                                    }
                                    ?.filter { monthlyReadingWithBook ->
                                        applyStatusFilter(monthlyReadingWithBook.monthlyReading, filter, now)
                                    } ?: emptyList()
                                Resource.Success(combinedList)
                            }
                        }
                    }
                }
                .catch { e ->
                    _monthlyReadingsWithBooks.value = Resource.Error("Erreur technique: ${e.localizedMessage}")
                }
                .collect { resource ->
                    _monthlyReadingsWithBooks.value = resource
                }
        }
    }

    private fun loadAllBooksIntoMap() {
        viewModelScope.launch {
            getBooksUseCase()
                .catch { e -> Log.e(TAG, "Erreur chargement livres: ", e) }
                .collect { resource ->
                    if (resource is Resource.Success) {
                        _booksMap.value = resource.data?.associateBy { it.id } ?: emptyMap()
                    }
                }
        }
    }

    private fun applyStatusFilter(monthlyReading: MonthlyReading, filter: ReadingsFilter, now: Date): Boolean {
        if (filter == ReadingsFilter.ALL) return true

        val analysisPhase = monthlyReading.analysisPhase
        val debatePhase = monthlyReading.debatePhase

        val isPast = debatePhase.date?.before(now) == true || debatePhase.status == PhaseStatus.COMPLETED
        val isInProgress = !isPast && (analysisPhase.date?.before(now) == true || analysisPhase.status == PhaseStatus.IN_PROGRESS)
        val isPlanned = !isPast && !isInProgress

        return when (filter) {
            ReadingsFilter.IN_PROGRESS -> isInProgress
            ReadingsFilter.PLANNED -> isPlanned
            ReadingsFilter.PAST -> isPast
            ReadingsFilter.ALL -> true
        }
    }

    fun goToPreviousMonth() {
        _currentMonthYear.update { it.apply { add(Calendar.MONTH, -1) } }
    }

    fun goToNextMonth() {
        _currentMonthYear.update { it.apply { add(Calendar.MONTH, 1) } }
    }

    fun setFilter(filter: ReadingsFilter) {
        _currentFilter.value = filter
    }
}