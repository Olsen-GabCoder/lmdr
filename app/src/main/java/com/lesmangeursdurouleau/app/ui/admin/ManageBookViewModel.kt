// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ManageBookViewModel.kt
package com.lesmangeursdurouleau.app.ui.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// import com.lesmangeursdurouleau.app.data.model.Book <-- N'est plus nécessaire ici
import com.lesmangeursdurouleau.app.domain.usecase.books.CreateBookWithFilesUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageBookUiState(
    val _placeholder: Boolean = true
)

@HiltViewModel
class ManageBookViewModel @Inject constructor(
    private val createBookWithFilesUseCase: CreateBookWithFilesUseCase
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _eventFlow = MutableSharedFlow<Resource<String>>()
    val eventFlow: SharedFlow<Resource<String>> = _eventFlow.asSharedFlow()

    fun createBook(
        title: String,
        author: String,
        synopsis: String,
        totalPages: Int,
        coverImage: ByteArray?,
        pdfUri: Uri?
    ) {
        viewModelScope.launch {
            if (title.isBlank() || author.isBlank()) {
                _eventFlow.emit(Resource.Error("Le titre et l'auteur sont obligatoires."))
                return@launch
            }

            _isLoading.value = true

            // === DÉBUT DE LA MODIFICATION ===
            // JUSTIFICATION: Le ViewModel n'a plus besoin de connaître l'objet `Book`.
            // Il passe simplement les données brutes au UseCase, qui va les traiter.
            // C'est un meilleur découplage des responsabilités.
            val result = createBookWithFilesUseCase(
                title = title,
                author = author,
                synopsis = synopsis,
                totalPages = totalPages,
                coverImage = coverImage,
                pdfUri = pdfUri
            )
            // === FIN DE LA MODIFICATION ===

            _eventFlow.emit(result)
            _isLoading.value = false
        }
    }
}