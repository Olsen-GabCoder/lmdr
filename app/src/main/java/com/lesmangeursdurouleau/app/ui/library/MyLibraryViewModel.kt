// PRÊT À COLLER - Créez un nouveau fichier MyLibraryViewModel.kt
package com.lesmangeursdurouleau.app.ui.library

import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksUseCase
import com.lesmangeursdurouleau.app.domain.usecase.library.GetLibraryEntriesUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

/**
 * Classe de données conçue pour l'UI.
 * Elle combine une entrée de la bibliothèque de l'utilisateur avec les détails complets du livre associé.
 */
@Parcelize
data class LibraryBookItem(
    val entry: UserLibraryEntry,
    val book: Book? // Nullable au cas où le livre aurait été supprimé de la base de données principale
) : Parcelable

@HiltViewModel
class MyLibraryViewModel @Inject constructor(
    private val getLibraryEntriesUseCase: GetLibraryEntriesUseCase,
    private val getBooksUseCase: GetBooksUseCase,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val currentUserId: String? = firebaseAuth.currentUser?.uid

    /**
     * Expose l'état de l'UI pour l'écran de la bibliothèque.
     * Ce Flow combine en temps réel les entrées de la bibliothèque de l'utilisateur avec
     * les détails des livres correspondants.
     */
    val libraryState: StateFlow<Resource<List<LibraryBookItem>>> =
        if (currentUserId.isNullOrBlank()) {
            // Si l'utilisateur n'est pas connecté, émettre un état d'erreur immédiatement.
            flowOf(Resource.Error<List<LibraryBookItem>>("Utilisateur non connecté."))
                .stateIn(viewModelScope, SharingStarted.Lazily, Resource.Loading())
        } else {
            // Combine les deux sources de données : la bibliothèque de l'utilisateur et la liste de tous les livres.
            getLibraryEntriesUseCase(currentUserId)
                .combine(getBooksUseCase()) { libraryResource, booksResource ->
                    // Gérer les états de chargement et d'erreur des deux sources
                    if (libraryResource is Resource.Loading || booksResource is Resource.Loading) {
                        return@combine Resource.Loading<List<LibraryBookItem>>()
                    }
                    if (libraryResource is Resource.Error) {
                        return@combine Resource.Error<List<LibraryBookItem>>(libraryResource.message ?: "Erreur de bibliothèque")
                    }
                    if (booksResource is Resource.Error) {
                        return@combine Resource.Error<List<LibraryBookItem>>(booksResource.message ?: "Erreur de livres")
                    }

                    // Si les deux sources sont chargées avec succès
                    if (libraryResource is Resource.Success && booksResource is Resource.Success) {
                        val libraryEntries = libraryResource.data ?: emptyList()
                        val booksMap = booksResource.data?.associateBy { it.id } ?: emptyMap()

                        // Mapper chaque entrée de bibliothèque avec les détails du livre correspondant.
                        val combinedList = libraryEntries.map { entry ->
                            LibraryBookItem(
                                entry = entry,
                                book = booksMap[entry.bookId]
                            )
                        }
                        return@combine Resource.Success(combinedList)
                    }

                    // Cas par défaut (ne devrait pas être atteint)
                    Resource.Error<List<LibraryBookItem>>("État inattendu des ressources.")
                }
                .stateIn(
                    scope = viewModelScope,
                    // Le Flow commence à être collecté lorsque l'UI est visible et s'arrête après 5s d'inactivité.
                    started = SharingStarted.WhileSubscribed(5000),
                    // L'état initial est le chargement.
                    initialValue = Resource.Loading()
                )
        }
}