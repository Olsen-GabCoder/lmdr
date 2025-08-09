// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ManageReadingsViewModel.kt
package com.lesmangeursdurouleau.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksByIdsUseCase
import com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings.GetMonthlyReadingsUseCase
import com.lesmangeursdurouleau.app.ui.readings.adapter.MonthlyReadingWithBook
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ManageReadingsViewModel @Inject constructor(
    private val getMonthlyReadingsUseCase: GetMonthlyReadingsUseCase,
    // === DÉBUT DE LA MODIFICATION ===
    // JUSTIFICATION: Nous injectons maintenant `GetBooksByIdsUseCase` au lieu de `GetBooksUseCase`
    // pour pouvoir effectuer une recherche ciblée et performante des livres.
    private val getBooksByIdsUseCase: GetBooksByIdsUseCase
    // === FIN DE LA MODIFICATION ===
) : ViewModel() {

    /**
     * Expose l'état de l'UI pour l'écran de gestion des lectures.
     * Ce Flow est optimisé pour ne charger que les livres strictement nécessaires.
     */
    val readingsListState: StateFlow<Resource<List<MonthlyReadingWithBook>>> =
    // === DÉBUT DE LA MODIFICATION ===
    // JUSTIFICATION: Remplacement de `combine` par `flatMapLatest` pour chaîner les appels de manière efficace.
    // `flatMapLatest` permet d'attendre le résultat du premier appel (les lectures) avant de
        // déclencher le second (les livres), en utilisant les données du premier.
        getMonthlyReadingsUseCase().flatMapLatest { readingsResource ->
            // Le bloc `flatMapLatest` doit retourner un `Flow`.
            when (readingsResource) {
                is Resource.Loading -> {
                    // Si les lectures sont en cours de chargement, on propage l'état Loading.
                    flowOf(Resource.Loading())
                }
                is Resource.Error -> {
                    // Si le chargement des lectures échoue, on propage l'erreur.
                    flowOf(Resource.Error(readingsResource.message ?: "Erreur de chargement des lectures"))
                }
                is Resource.Success -> {
                    val readings = readingsResource.data ?: emptyList()

                    // Si la liste des lectures est vide, inutile de chercher des livres. On retourne un succès avec une liste vide.
                    if (readings.isEmpty()) {
                        flowOf(Resource.Success(emptyList()))
                    } else {
                        // 1. On extrait la liste unique des `bookId` nécessaires.
                        val bookIds = readings.map { it.bookId }.distinct()

                        // 2. On utilise notre nouveau UseCase pour ne récupérer QUE ces livres.
                        // L'opérateur `.map` transforme le résultat (`Resource<List<Book>>`) en notre état final (`Resource<List<MonthlyReadingWithBook>>`).
                        getBooksByIdsUseCase(bookIds).map { booksResource ->
                            handleBookResource(booksResource, readings)
                        }
                    }
                }
            }
        }
            // === FIN DE LA MODIFICATION ===
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )

    /**
     * Fonction d'aide pour combiner les lectures et les livres une fois que les deux sont chargés.
     * Cette logique était auparavant dans le `combine`, elle est maintenant isolée ici pour plus de clarté.
     */
    private fun handleBookResource(
        booksResource: Resource<List<Book>>,
        readings: List<MonthlyReading>
    ): Resource<List<MonthlyReadingWithBook>> {
        return when (booksResource) {
            is Resource.Loading -> Resource.Loading()
            is Resource.Error -> Resource.Error(booksResource.message ?: "Erreur de chargement des livres")
            is Resource.Success -> {
                val booksMap = booksResource.data?.associateBy { it.id } ?: emptyMap()
                val combinedList = readings.map { reading ->
                    MonthlyReadingWithBook(
                        monthlyReading = reading,
                        book = booksMap[reading.bookId] // Le livre peut être null si non trouvé, ce qui est géré par la vue.
                    )
                }
                Resource.Success(combinedList)
            }
        }
    }
}