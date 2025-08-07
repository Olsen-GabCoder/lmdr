// PRÊT À COLLER - Créez un nouveau fichier ManageReadingsViewModel.kt dans un nouveau package (ex: ui/admin/managereadings)
package com.lesmangeursdurouleau.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lesmangeursdurouleau.app.domain.usecase.books.GetBooksUseCase
import com.lesmangeursdurouleau.app.domain.usecase.monthlyreadings.GetMonthlyReadingsUseCase
import com.lesmangeursdurouleau.app.ui.readings.adapter.MonthlyReadingWithBook
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ManageReadingsViewModel @Inject constructor(
    private val getMonthlyReadingsUseCase: GetMonthlyReadingsUseCase,
    private val getBooksUseCase: GetBooksUseCase
) : ViewModel() {

    /**
     * Expose l'état de l'UI pour l'écran de gestion des lectures.
     * Ce Flow combine la liste de toutes les lectures mensuelles avec les détails des livres associés.
     */
    val readingsListState: StateFlow<Resource<List<MonthlyReadingWithBook>>> =
        // On appelle `getMonthlyReadingsUseCase` sans argument pour récupérer TOUTES les lectures.
        getMonthlyReadingsUseCase()
            .combine(getBooksUseCase()) { readingsResource, booksResource ->
                // La logique de combinaison est la même que dans le ReadingsViewModel,
                // mais sans la complexité des filtres temporels.
                if (readingsResource is Resource.Loading || booksResource is Resource.Loading) {
                    return@combine Resource.Loading()
                }
                if (readingsResource is Resource.Error) {
                    return@combine Resource.Error(readingsResource.message ?: "Erreur de chargement des lectures")
                }
                if (booksResource is Resource.Error) {
                    return@combine Resource.Error(booksResource.message ?: "Erreur de chargement des livres")
                }
                if (readingsResource is Resource.Success && booksResource is Resource.Success) {
                    val readings = readingsResource.data ?: emptyList()
                    val booksMap = booksResource.data?.associateBy { it.id } ?: emptyMap()

                    val combinedList = readings.map { reading ->
                        MonthlyReadingWithBook(
                            monthlyReading = reading,
                            book = booksMap[reading.bookId]
                        )
                    }
                    return@combine Resource.Success(combinedList)
                }
                // Cas par défaut peu probable
                Resource.Error("État inattendu des ressources.")
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )
}