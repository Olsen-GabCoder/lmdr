// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ReadingsViewModel.kt
package com.lesmangeursdurouleau.app.ui.readings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.Phase
import com.lesmangeursdurouleau.app.data.model.PhaseStatus
import com.lesmangeursdurouleau.app.data.repository.MonthlyReadingRepository
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksByIdsUseCase
import com.lesmangeursdurouleau.app.ui.readings.adapter.MonthlyReadingUiItem
import com.lesmangeursdurouleau.app.ui.readings.adapter.PhaseDisplayStatus
import com.lesmangeursdurouleau.app.ui.readings.adapter.PhaseUiItem
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
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
    private val getBooksByIdsUseCase: GetBooksByIdsUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private companion object {
        private const val TAG = "ReadingsViewModel"
    }

    private val _currentFilter = MutableStateFlow(ReadingsFilter.ALL)
    val currentFilter: StateFlow<ReadingsFilter> = _currentFilter.asStateFlow()

    val monthlyReadings: StateFlow<Resource<List<MonthlyReadingUiItem>>>

    private val subtitleDateFormatter = SimpleDateFormat("MMMM yyyy", Locale.FRENCH)
    private val phaseDateFormatter = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)

    init {
        monthlyReadings = setupMonthlyReadingsFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )
    }

    private fun setupMonthlyReadingsFlow(): Flow<Resource<List<MonthlyReadingUiItem>>> {
        val minuteTicker = flow {
            while (true) { emit(Unit); delay(60_000L) }
        }

        return combine(_currentFilter, minuteTicker) { filter, _ -> filter }
            .flatMapLatest { filter ->
                monthlyReadingRepository.getAllMonthlyReadings().flatMapLatest { readingsResource ->
                    handleReadingsResource(readingsResource, filter)
                }
            }.catch { e ->
                Log.e(TAG, "Erreur dans le flux principal de lectures", e)
                emit(Resource.Error("Erreur technique: ${e.localizedMessage}"))
            }
    }

    private fun handleReadingsResource(
        readingsResource: Resource<List<MonthlyReading>>,
        filter: ReadingsFilter
    ): Flow<Resource<List<MonthlyReadingUiItem>>> {
        return when (readingsResource) {
            is Resource.Loading -> flowOf(Resource.Loading())
            is Resource.Error -> flowOf(Resource.Error(readingsResource.message ?: "Erreur de chargement des lectures"))
            is Resource.Success -> {
                val allReadings = readingsResource.data ?: emptyList()
                val now = Date()

                val filteredReadings = allReadings.filter { reading ->
                    val status = calculateGlobalStatus(reading, now)
                    when (filter) {
                        ReadingsFilter.ALL -> true
                        ReadingsFilter.PAST -> status == PhaseDisplayStatus.COMPLETED
                        ReadingsFilter.IN_PROGRESS -> status == PhaseDisplayStatus.IN_PROGRESS
                        ReadingsFilter.PLANNED -> status == PhaseDisplayStatus.PLANIFIED
                    }
                }

                if (filteredReadings.isEmpty()) {
                    flowOf(Resource.Success(emptyList()))
                } else {
                    val bookIds = filteredReadings.map { it.bookId }.distinct()
                    getBooksByIdsUseCase(bookIds).map { booksResource ->
                        combineAndConvertToUiItems(filteredReadings, booksResource)
                    }
                }
            }
        }
    }

    private fun combineAndConvertToUiItems(
        readings: List<MonthlyReading>,
        booksResource: Resource<List<Book>>
    ): Resource<List<MonthlyReadingUiItem>> {
        return when (booksResource) {
            is Resource.Loading -> Resource.Loading()
            is Resource.Error -> Resource.Error(booksResource.message ?: "Erreur de chargement des livres")
            is Resource.Success -> {
                val booksMap = booksResource.data?.associateBy { it.id } ?: emptyMap()
                val now = Date()

                val uiItems = readings.mapNotNull { reading ->
                    val book = booksMap[reading.bookId] ?: return@mapNotNull null

                    val analysisStatus = calculatePhaseStatus(reading.analysisPhase, now)
                    val debateStatus = calculatePhaseStatus(reading.debatePhase, now)
                    val isAnalysisJoinable = analysisStatus == PhaseDisplayStatus.IN_PROGRESS && !reading.analysisPhase.meetingLink.isNullOrBlank()
                    val isDebateJoinable = debateStatus == PhaseDisplayStatus.IN_PROGRESS && !reading.debatePhase.meetingLink.isNullOrBlank()

                    MonthlyReadingUiItem(
                        id = reading.id, book = book, rawReading = reading,
                        subtitle = context.getString(R.string.post_subtitle_template, subtitleDateFormatter.format(reading.getCalendar().time).replaceFirstChar { it.uppercase() }),
                        customDescription = reading.customDescription?.let { "\"$it\"" },
                        analysisPhase = PhaseUiItem(name = context.getString(R.string.analysis_phase_label), dateText = reading.analysisPhase.date?.let { phaseDateFormatter.format(it) } ?: "Date non définie", iconResId = R.drawable.ic_analysis, status = analysisStatus, meetingLink = reading.analysisPhase.meetingLink),
                        debatePhase = PhaseUiItem(name = context.getString(R.string.debate_phase_label), dateText = reading.debatePhase.date?.let { phaseDateFormatter.format(it) } ?: "Date non définie", iconResId = R.drawable.ic_people, status = debateStatus, meetingLink = reading.debatePhase.meetingLink),
                        isJoinable = isAnalysisJoinable || isDebateJoinable,
                        joinableLink = if (isAnalysisJoinable) reading.analysisPhase.meetingLink else reading.debatePhase.meetingLink
                    )
                }
                Resource.Success(uiItems)
            }
        }
    }

    private fun calculatePhaseStatus(phase: Phase, now: Date): PhaseDisplayStatus {
        val today = Calendar.getInstance().apply { time = now; clearTime() }.time
        val phaseDate = phase.date?.let { Calendar.getInstance().apply { time = it; clearTime() }.time }

        return when {
            phase.status == PhaseStatus.COMPLETED -> PhaseDisplayStatus.COMPLETED
            phase.status == PhaseStatus.IN_PROGRESS -> PhaseDisplayStatus.IN_PROGRESS
            phaseDate == null -> PhaseDisplayStatus.PLANIFIED
            phaseDate.before(today) -> PhaseDisplayStatus.COMPLETED
            phaseDate == today -> PhaseDisplayStatus.IN_PROGRESS
            else -> PhaseDisplayStatus.PLANIFIED
        }
    }

    private fun calculateGlobalStatus(monthlyReading: MonthlyReading, now: Date): PhaseDisplayStatus {
        val isPast = calculatePhaseStatus(monthlyReading.debatePhase, now) == PhaseDisplayStatus.COMPLETED
        val isInProgress = !isPast && calculatePhaseStatus(monthlyReading.analysisPhase, now) != PhaseDisplayStatus.PLANIFIED

        return when {
            isPast -> PhaseDisplayStatus.COMPLETED
            isInProgress -> PhaseDisplayStatus.IN_PROGRESS
            else -> PhaseDisplayStatus.PLANIFIED
        }
    }

    fun setFilter(filter: ReadingsFilter) { _currentFilter.value = filter }

    private fun MonthlyReading.getCalendar(): Calendar = Calendar.getInstance().apply { set(Calendar.YEAR, year); set(Calendar.MONTH, month - 1) }
    private fun Calendar.clearTime() { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
}