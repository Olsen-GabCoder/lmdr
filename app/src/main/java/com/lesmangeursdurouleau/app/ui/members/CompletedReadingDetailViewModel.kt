// PRÊT À COLLER - Fichier 100% complet et corrigé
package com.lesmangeursdurouleau.app.ui.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// La data class pour l'UI, combinant les informations nécessaires.
data class CompletedReadingDetailUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val completionDate: java.util.Date? = null,
    val error: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CompletedReadingDetailViewModel @Inject constructor(
    private val readingRepository: ReadingRepository,
    private val socialRepository: SocialRepository,
    private val bookRepository: BookRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val targetUserId: String = savedStateHandle.get<String>("userId")!!
    private val bookId: String = savedStateHandle.get<String>("bookId")!!

    val currentUserId: StateFlow<String?> = MutableStateFlow(firebaseAuth.currentUser?.uid).asStateFlow()

    // StateFlow pour l'état complet de l'UI
    val uiState: StateFlow<CompletedReadingDetailUiState> =
        readingRepository.getCompletedReadingDetail(targetUserId, bookId)
            .flatMapLatest { readingResource ->
                when (readingResource) {
                    is Resource.Loading -> flowOf(CompletedReadingDetailUiState(isLoading = true))
                    is Resource.Error -> flowOf(CompletedReadingDetailUiState(isLoading = false, error = readingResource.message))
                    is Resource.Success -> {
                        val reading = readingResource.data
                        if (reading != null) {
                            bookRepository.getBookById(reading.bookId)
                                .map { bookResource ->
                                    CompletedReadingDetailUiState(
                                        isLoading = bookResource is Resource.Loading,
                                        book = (bookResource as? Resource.Success)?.data,
                                        completionDate = reading.completionDate,
                                        error = (bookResource as? Resource.Error)?.message
                                    )
                                }
                        } else {
                            flowOf(CompletedReadingDetailUiState(isLoading = false, error = "Lecture terminée non trouvée."))
                        }
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = CompletedReadingDetailUiState(isLoading = true)
            )

    val comments: StateFlow<Resource<List<Comment>>> = socialRepository.getCommentsForBook(bookId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Resource.Loading()
        )

    // CORRIGÉ : L'appel est maintenant fait à 'isBookLikedByUser', qui est la méthode correcte
    // pour aimer un livre de manière générale, et non une lecture active spécifique.
    val isReadingLikedByCurrentUser: StateFlow<Resource<Boolean>> = currentUserId.flatMapLatest { id ->
        if (id == null) flowOf(Resource.Success(false))
        else socialRepository.isBookLikedByUser(bookId, id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Resource.Loading()
    )

    // NOTE : Cette méthode est correcte car elle récupère le compteur global du livre.
    val readingLikesCount: StateFlow<Resource<Int>> = socialRepository.getBookLikesCount(bookId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Resource.Loading()
        )

    // NOTE : Cette méthode est correcte car elle aime le livre de manière globale.
    fun toggleLikeOnReading() {
        val uid = currentUserId.value ?: return
        viewModelScope.launch {
            // Note pour la maintenance future : si on voulait créer une notification sociale
            // de type "X a aimé le livre que Y a lu", cette méthode devrait être changée pour
            // appeler une Cloud Function. Pour l'instant, elle n'a pas d'effet social.
            socialRepository.toggleLikeOnBook(bookId, uid)
        }
    }

    fun toggleLikeOnComment(commentId: String) {
        val uid = currentUserId.value ?: return
        viewModelScope.launch {
            socialRepository.toggleLikeOnComment(bookId, commentId, uid)
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            socialRepository.deleteCommentOnBook(bookId, commentId)
        }
    }

    fun addComment(commentText: String) {
        val user = firebaseAuth.currentUser ?: return
        val comment = Comment(
            userId = user.uid,
            userName = user.displayName ?: "Utilisateur inconnu",
            userPhotoUrl = user.photoUrl?.toString(),
            commentText = commentText.trim(),
            timestamp = Timestamp.now(),
            targetUserId = targetUserId,
            bookId = bookId
        )
        viewModelScope.launch {
            socialRepository.addCommentOnBook(bookId, comment)
        }
    }

    fun isCommentLikedByCurrentUser(commentId: String): Flow<Resource<Boolean>> {
        return currentUserId.flatMapLatest { id ->
            if (id == null) flowOf(Resource.Success(false))
            else socialRepository.isCommentLikedByCurrentUser(bookId, commentId, id)
        }
    }
}