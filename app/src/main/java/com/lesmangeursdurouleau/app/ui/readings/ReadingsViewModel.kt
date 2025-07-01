// PRÊT À COLLER - Remplacez tout le contenu de votre fichier ReadingsViewModel.kt par ceci.
package com.lesmangeursdurouleau.app.ui.readings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.PhaseStatus
import com.lesmangeursdurouleau.app.data.repository.AppConfigRepository
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksUseCase
import com.lesmangeursdurouleau.app.domain.usecase.permissions.CheckUserEditPermissionUseCase // NOUVEL IMPORT
import com.lesmangeursdurouleau.app.ui.readings.adapter.MonthlyReadingWithBook
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val getBooksUseCase: GetBooksUseCase,
    // NOUVELLE INJECTION : Le Use Case pour la logique de permission
    private val checkUserEditPermissionUseCase: CheckUserEditPermissionUseCase,
    private val firebaseAuth: FirebaseAuth
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

    // MODIFIÉ: Ce StateFlow est maintenant alimenté directement par le Use Case.
    val canEditReadings: StateFlow<Boolean> = checkUserEditPermissionUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        loadAllBooksIntoMap()
        setupMonthlyReadingsFlow()
    }

    // SUPPRIMÉ: Les méthodes setupAdminSecretCodeTimestampListener et setupUserEditPermissionListener sont
    // désormais inutiles car leur logique est encapsulée dans le CheckUserEditPermissionUseCase.
    // La méthode forcePermissionCheck est également supprimée car le Use Case basé sur Flow se mettra à jour automatiquement.

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupMonthlyReadingsFlow() {
        viewModelScope.launch {
            combine(_currentMonthYear, _currentFilter) { calendar, filter ->
                Pair(calendar, filter)
            }
                .flatMapLatest { (calendar, filter) ->
                    // La logique de récupération reste la même.
                    if (filter == ReadingsFilter.ALL) {
                        monthlyReadingRepository.getMonthlyReadings(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH) + 1
                        )
                    } else {
                        monthlyReadingRepository.getAllMonthlyReadings()
                    }
                }
                .combine(_booksMap) { readingsResource, booksMap ->
                    when (readingsResource) {
                        is Resource.Loading -> Resource.Loading()
                        is Resource.Error -> Resource.Error(readingsResource.message ?: "Erreur de chargement.")
                        is Resource.Success -> {
                            val combinedList = readingsResource.data
                                ?.map { monthlyReading ->
                                    MonthlyReadingWithBook(monthlyReading, booksMap[monthlyReading.bookId])
                                }
                                ?.filter { monthlyReadingWithBook ->
                                    applyStatusFilter(monthlyReadingWithBook.monthlyReading, _currentFilter.value)
                                } ?: emptyList()
                            Resource.Success(combinedList)
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

    // Le reste du ViewModel reste identique (logique de filtre et de navigation temporelle)
    private fun applyStatusFilter(monthlyReading: MonthlyReading, filter: ReadingsFilter): Boolean {
        if (filter == ReadingsFilter.ALL) return true

        val now = Date()
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