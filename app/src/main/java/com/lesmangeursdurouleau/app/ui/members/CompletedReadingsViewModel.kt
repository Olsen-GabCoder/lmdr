package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksByIdsUseCase
import com.lesmangeursdurouleau.app.domain.usecase.library.GetCompletedLibraryEntriesUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class CompletedReadingUiModel(
    val libraryEntry: UserLibraryEntry,
    val book: Book?
)

data class SortOptions(
    val orderBy: String = "lastReadDate",
    val direction: SortDirection = SortDirection.DESCENDING
)

enum class SortDirection {
    ASCENDING, DESCENDING
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CompletedReadingsViewModel @Inject constructor(
    private val bookRepository: BookRepository, // Conservé pour la fonction de suppression
    private val getCompletedLibraryEntriesUseCase: GetCompletedLibraryEntriesUseCase, // NOUVEAU et CORRECT
    private val getBooksByIdsUseCase: GetBooksByIdsUseCase, // NOUVEAU et OPTIMISÉ
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "CompletedReadingsVM"
    }

    private val userId: String = savedStateHandle.get<String>("userId")
        ?: throw IllegalArgumentException("userId est manquant")

    private val _sortOptions = MutableStateFlow(SortOptions())

    val completedReadings: StateFlow<Resource<List<CompletedReadingUiModel>>> =
        _sortOptions.flatMapLatest { options ->
            // Étape 1: Récupérer uniquement les entrées de bibliothèque terminées (léger et rapide)
            getCompletedLibraryEntriesUseCase(userId, options).flatMapLatest { entriesResource ->
                when (entriesResource) {
                    is Resource.Loading -> {
                        flowOf(Resource.Loading())
                    }
                    is Resource.Error -> {
                        flowOf(Resource.Error(entriesResource.message ?: "Erreur lectures"))
                    }
                    is Resource.Success -> {
                        val completedEntries = entriesResource.data ?: emptyList()
                        if (completedEntries.isEmpty()) {
                            // Si pas de livres lus, inutile de continuer, on renvoie une liste vide.
                            flowOf(Resource.Success(emptyList()))
                        } else {
                            val bookIds = completedEntries.map { it.bookId }.distinct()

                            // Étape 2: Récupérer les détails UNIQUEMENT des livres nécessaires (très performant)
                            getBooksByIdsUseCase(bookIds).map { booksResource ->
                                when (booksResource) {
                                    is Resource.Loading -> Resource.Loading()
                                    is Resource.Error -> Resource.Error(booksResource.message ?: "Erreur livres")
                                    is Resource.Success -> {
                                        val booksMap = booksResource.data?.associateBy { it.id } ?: emptyMap()

                                        // Étape 3: Combiner les deux listes de données
                                        val combinedList = completedEntries.map { entry ->
                                            CompletedReadingUiModel(
                                                libraryEntry = entry,
                                                book = booksMap[entry.bookId]
                                            )
                                        }

                                        // Étape 4: Trier en mémoire (obligatoire pour trier par titre/auteur)
                                        val sortedList = sortInMemory(combinedList, options)

                                        Log.d(TAG, "Affichage de ${sortedList.size} lectures terminées.")
                                        Resource.Success(sortedList)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Resource.Loading()
        )

    private fun sortInMemory(list: List<CompletedReadingUiModel>, options: SortOptions): List<CompletedReadingUiModel> {
        val comparator = compareBy<CompletedReadingUiModel> {
            when (options.orderBy) {
                "title" -> it.book?.title?.lowercase(Locale.ROOT)
                "author" -> it.book?.author?.lowercase(Locale.ROOT)
                else -> null // Pour lastReadDate, le tri est déjà fait par Firestore
            }
        }

        // Le tri par date est déjà géré par la requête Firestore, qui est plus efficace.
        // On ne trie ici que si l'option est titre ou auteur.
        val sortedList = if (options.orderBy == "title" || options.orderBy == "author") {
            list.sortedWith(comparator)
        } else {
            list
        }

        return if (options.direction == SortDirection.DESCENDING) {
            sortedList.reversed() // `List.reversed()` est compatible avec toutes les versions d'API
        } else {
            sortedList
        }
    }

    private val _deleteStatus = MutableSharedFlow<Resource<Unit>>()
    val deleteStatus: SharedFlow<Resource<Unit>> = _deleteStatus.asSharedFlow()

    fun setSortOption(orderBy: String, direction: SortDirection) {
        // Le tri par titre et auteur ne peut pas être fait sur Firestore car les
        // données ne sont pas dans la même collection. On les fera en mémoire.
        // Le tri par date sera fait par Firestore pour plus d'efficacité.
        _sortOptions.value = SortOptions(orderBy, direction)
    }

    fun removeReadingFromLibrary(bookId: String) {
        viewModelScope.launch {
            val result = bookRepository.removeBookFromUserLibrary(userId, bookId)
            _deleteStatus.emit(result)
        }
    }
}