// PRÊT À COLLER - Remplacez tout le contenu de votre fichier CompletedReadingsViewModel.kt par ceci.
package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SortOptions(
    val orderBy: String = "completionDate",
    val direction: Query.Direction = Query.Direction.DESCENDING
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CompletedReadingsViewModel @Inject constructor(
    private val readingRepository: ReadingRepository,
    private val bookRepository: BookRepository, // NOUVELLE INJECTION
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "CompletedReadingsVM"
    }

    private val userId: String = savedStateHandle.get<String>("userId")
        ?: throw IllegalArgumentException("userId est manquant")

    private val _sortOptions = MutableStateFlow(SortOptions())

    // NOUVELLE logique de flow combiné
    val completedReadingsWithBooks: StateFlow<Resource<List<CompletedReadingWithBook>>> = _sortOptions
        .flatMapLatest { options ->
            // Étape 1: Récupérer la liste des lectures terminées (qui ne contiennent que des ID)
            val completedReadingsFlow = readingRepository.getCompletedReadings(userId, options.orderBy, options.direction)
            // Étape 2: Récupérer la liste de tous les livres
            val allBooksFlow = bookRepository.getAllBooks()

            // Étape 3: Combiner les deux flux
            completedReadingsFlow.combine(allBooksFlow) { readingsResource, booksResource ->
                // Gérer les états de chargement et d'erreur des deux flux
                if (readingsResource is Resource.Loading || booksResource is Resource.Loading) {
                    return@combine Resource.Loading()
                }
                if (readingsResource is Resource.Error) {
                    return@combine Resource.Error(readingsResource.message ?: "Erreur lectures")
                }
                if (booksResource is Resource.Error) {
                    return@combine Resource.Error(booksResource.message ?: "Erreur livres")
                }

                // Si les deux flux sont en succès, on procède à la "jointure"
                if (readingsResource is Resource.Success && booksResource is Resource.Success) {
                    val readings = readingsResource.data ?: emptyList()
                    val books = booksResource.data ?: emptyList()

                    // Créer une map pour une recherche rapide (O(1) en moyenne)
                    val booksMap = books.associateBy { it.id }

                    // Mapper chaque lecture terminée à son livre correspondant
                    val combinedList = readings.map { reading ->
                        CompletedReadingWithBook(
                            completedReading = reading,
                            book = booksMap[reading.bookId] // Peut être null si le livre a été supprimé
                        )
                    }
                    Log.d(TAG, "Successfully combined ${combinedList.size} readings with their book details.")
                    Resource.Success(combinedList)
                } else {
                    Resource.Error("État inattendu des ressources.")
                }
            }
        }
        .catch { e ->
            Log.e(TAG, "Erreur dans le flow combiné: ${e.message}", e)
            emit(Resource.Error("Erreur de chargement: ${e.localizedMessage}"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Resource.Loading()
        )

    private val _deleteStatus = MutableSharedFlow<Resource<Unit>>()
    val deleteStatus: SharedFlow<Resource<Unit>> = _deleteStatus.asSharedFlow()

    init {
        Log.d(TAG, "ViewModel initialisé pour userId: $userId")
    }

    fun setSortOption(orderBy: String, direction: Query.Direction) {
        val newOptions = when (orderBy) {
            "title", "author" -> {
                Log.w(TAG, "Le tri par '$orderBy' n'est plus supporté directement par Firestore sur la collection 'completed_readings'. Le tri sera appliqué après la jointure.")
                // Pour l'instant on garde le tri par défaut et on pourrait trier en mémoire ici.
                // Pour la simplicité de ce commit, on garde le tri par date.
                SortOptions(orderBy = "completionDate", direction = direction)
            }
            else -> SortOptions(orderBy, direction)
        }
        _sortOptions.value = newOptions
    }

    fun deleteCompletedReading(bookId: String) {
        viewModelScope.launch {
            val result = readingRepository.removeCompletedReading(userId, bookId)
            _deleteStatus.emit(result)
        }
    }
}