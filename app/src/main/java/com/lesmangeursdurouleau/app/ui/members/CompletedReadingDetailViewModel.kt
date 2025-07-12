// Fichier complet : CompletedReadingDetailViewModel.kt

package com.lesmangeursdurouleau.app.ui.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
// JUSTIFICATION SUPPRESSION : DocumentSnapshot n'est plus nécessaire car la pagination manuelle est supprimée.
// import com.google.firebase.firestore.DocumentSnapshot
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.repository.BookRepository
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
    private val localUserPreferencesRepository: LocalUserPreferencesRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val targetUserId: String = savedStateHandle.get<String>("userId")!!
    private val bookId: String = savedStateHandle.get<String>("bookId")!!

    // JUSTIFICATION MODIFICATION : Suppression de `_comments` et `lastVisibleCommentDoc`.
    // L'état est maintenant géré directement par un `StateFlow` réactif dérivé des flux sources.

    // JUSTIFICATION AJOUT : `comments` est maintenant dérivé directement en combinant le stream temps réel
    // et les préférences de masquage. Cela remplace l'approche de chargement manuel (`loadComments`)
    // et restaure la réactivité instantanée de l'UI aux changements de likes ou de nouveaux commentaires.
    val comments: StateFlow<Resource<List<UiComment>>> = combine(
        socialRepository.getCommentsForReadingStream(targetUserId, bookId),
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

    // JUSTIFICATION SUPPRESSION : `init` n'a plus besoin d'appeler `loadComments()`.
    // JUSTIFICATION SUPPRESSION : `loadComments()` est supprimée car elle utilisait une lecture ponctuelle.
    // Elle est remplacée par le flux réactif défini dans `comments`.

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
            socialRepository.toggleLikeOnCommentForReading(targetUserId, bookId, commentId, uid)
        }
    }

    fun hideComment(commentId: String) {
        viewModelScope.launch {
            localUserPreferencesRepository.hideComment(commentId)
            // JUSTIFICATION SUPPRESSION : `loadComments()` est supprimé. Le `combine` dans `comments`
            // détectera le changement dans `getHiddenCommentIds()` et mettra à jour l'UI automatiquement.
        }
    }

    fun unhideComment(commentId: String) {
        viewModelScope.launch {
            localUserPreferencesRepository.unhideComment(commentId)
            // JUSTIFICATION SUPPRESSION : `loadComments()` est supprimé pour la même raison.
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            socialRepository.deleteCommentOnReading(targetUserId, bookId, commentId)
            // L'écouteur temps réel (`getCommentsForReadingStream`) détectera la suppression et mettra à jour l'UI.
        }
    }

    fun addComment(commentText: String) {
        val user = firebaseAuth.currentUser ?: return
        val comment = Comment(
            userId = user.uid,
            userName = user.displayName ?: "Utilisateur inconnu",
            userPhotoUrl = user.photoUrl?.toString(),
            commentText = commentText.trim(),
            bookId = bookId
        )
        viewModelScope.launch {
            socialRepository.addCommentOnReading(targetUserId, bookId, comment)
            // L'écouteur temps réel (`getCommentsForReadingStream`) détectera l'ajout et mettra à jour l'UI.
        }
    }

    fun isCommentLikedByCurrentUser(commentId: String): Flow<Resource<Boolean>> {
        return currentUserId.flatMapLatest { id ->
            if (id == null) flowOf(Resource.Success(false))
            else socialRepository.isCommentLikedByUserOnReading(targetUserId, bookId, commentId, id)
        }
    }
}