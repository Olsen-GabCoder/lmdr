package com.lesmangeursdurouleau.app.ui.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.repository.BookRepository
// AJOUT : Import de la dépendance pour la persistance locale.
import com.lesmangeursdurouleau.app.data.repository.LocalUserPreferencesRepository
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    // AJOUT : Injection du repository pour les préférences locales (commentaires masqués).
    private val localUserPreferencesRepository: LocalUserPreferencesRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val targetUserId: String = savedStateHandle.get<String>("userId")!!
    private val bookId: String = savedStateHandle.get<String>("bookId")!!

    val currentUserId: StateFlow<String?> = MutableStateFlow(firebaseAuth.currentUser?.uid).asStateFlow()

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

    // MODIFICATION : Le flow `comments` expose maintenant des `UiComment` au lieu de `Comment`.
    val comments: StateFlow<Resource<List<UiComment>>> =
        combine(
            socialRepository.getCommentsForBook(bookId),
            localUserPreferencesRepository.getHiddenCommentIds()
        ) { commentsResource, hiddenIds ->
            when (commentsResource) {
                is Resource.Success -> {
                    val uiComments = commentsResource.data?.map { comment ->
                        UiComment(
                            comment = comment,
                            isHidden = comment.commentId in hiddenIds
                        )
                    } ?: emptyList()
                    Resource.Success(uiComments)
                }
                is Resource.Error -> Resource.Error(commentsResource.message ?: "Erreur de chargement des commentaires")
                is Resource.Loading -> Resource.Loading()
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Resource.Loading()
        )

    val isReadingLikedByCurrentUser: StateFlow<Resource<Boolean>> = currentUserId.flatMapLatest { id ->
        if (id == null) flowOf(Resource.Success(false))
        else socialRepository.isBookLikedByUser(bookId, id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Resource.Loading()
    )

    val readingLikesCount: StateFlow<Resource<Int>> = socialRepository.getBookLikesCount(bookId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Resource.Loading()
        )

    fun toggleLikeOnReading() {
        val uid = currentUserId.value ?: return
        viewModelScope.launch {
            socialRepository.toggleLikeOnBook(bookId, uid)
        }
    }

    fun toggleLikeOnComment(commentId: String) {
        val uid = currentUserId.value ?: return
        viewModelScope.launch {
            socialRepository.toggleLikeOnComment(bookId, commentId, uid)
        }
    }

    // AJOUT : Fonction publique pour masquer un commentaire.
    fun hideComment(commentId: String) {
        viewModelScope.launch {
            localUserPreferencesRepository.hideComment(commentId)
        }
    }

    // AJOUT : Fonction publique pour démasquer un commentaire.
    fun unhideComment(commentId: String) {
        viewModelScope.launch {
            localUserPreferencesRepository.unhideComment(commentId)
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