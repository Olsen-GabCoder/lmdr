// PRÊT À COLLER - Fichier 2/3
package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CurrentReadingExperience(
    val reading: UserBookReading,
    val book: Book,
    val comments: List<Comment> = emptyList(),
    val likesCount: Int = 0,
    val isLikedByCurrentUser: Boolean = false
)

data class PublicProfileUiState(
    val profileLoadState: ProfileLoadState = ProfileLoadState.Loading,
    val user: User? = null,
    val isFollowing: Boolean = false,
    val isOwnedProfile: Boolean = false,
    val readingExperience: CurrentReadingExperience? = null
)

sealed class ProfileLoadState {
    object Loading : ProfileLoadState()
    object Success : ProfileLoadState()
    data class Error(val message: String) : ProfileLoadState()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val socialRepository: SocialRepository,
    private val readingRepository: ReadingRepository,
    private val bookRepository: BookRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PublicProfileViewModel"
    }

    private val targetUserId: String = savedStateHandle.get<String>("userId")!!
    val currentUserId: String? = firebaseAuth.currentUser?.uid

    private val _uiState = MutableStateFlow(PublicProfileUiState())
    val uiState: StateFlow<PublicProfileUiState> = _uiState.asStateFlow()

    private val _userInteractionEvents = MutableSharedFlow<String>()
    val userInteractionEvents: SharedFlow<String> = _userInteractionEvents.asSharedFlow()

    init {
        loadProfileData()
    }

    private fun loadProfileData() {
        viewModelScope.launch {
            val userProfileFlow = userProfileRepository.getUserById(targetUserId)
            val isFollowingFlow = if (currentUserId != null && currentUserId != targetUserId) {
                socialRepository.isFollowing(currentUserId, targetUserId)
            } else {
                flowOf(Resource.Success(false))
            }

            val readingExperienceFlow: Flow<Resource<CurrentReadingExperience?>> = readingRepository.getCurrentReading(targetUserId)
                .flatMapLatest { readingResource ->
                    val reading = (readingResource as? Resource.Success)?.data
                    if (reading == null || reading.bookId.isBlank()) {
                        return@flatMapLatest flowOf(Resource.Success(null))
                    }

                    val bookFlow = bookRepository.getBookById(reading.bookId)
                    val commentsFlow = socialRepository.getCommentsForBook(reading.bookId)
                    val likesCountFlow = socialRepository.getBookLikesCount(reading.bookId)
                    val isLikedFlow = currentUserId?.let { uid -> socialRepository.isBookLikedByUser(reading.bookId, uid) } ?: flowOf(Resource.Success(false))

                    combine(bookFlow, commentsFlow, likesCountFlow, isLikedFlow) { bookRes, commentsRes, likesRes, isLikedRes ->
                        if (bookRes is Resource.Success && bookRes.data != null) {
                            Resource.Success(
                                CurrentReadingExperience(
                                    reading = reading,
                                    book = bookRes.data,
                                    comments = (commentsRes as? Resource.Success)?.data ?: emptyList(),
                                    likesCount = (likesRes as? Resource.Success)?.data ?: 0,
                                    isLikedByCurrentUser = (isLikedRes as? Resource.Success)?.data ?: false
                                )
                            )
                        } else if (bookRes is Resource.Error) {
                            Resource.Error("Livre associé non trouvé: ${bookRes.message}")
                        } else {
                            Resource.Loading()
                        }
                    }
                }

            combine(userProfileFlow, isFollowingFlow, readingExperienceFlow) { user, isFollowing, readingExperience ->
                if (user is Resource.Loading) return@combine PublicProfileUiState(profileLoadState = ProfileLoadState.Loading)
                if (user is Resource.Error) return@combine PublicProfileUiState(profileLoadState = ProfileLoadState.Error(user.message ?: "Erreur utilisateur"))

                PublicProfileUiState(
                    profileLoadState = ProfileLoadState.Success,
                    user = (user as Resource.Success).data,
                    isFollowing = (isFollowing as? Resource.Success)?.data ?: false,
                    isOwnedProfile = targetUserId == currentUserId,
                    readingExperience = (readingExperience as? Resource.Success)?.data
                )
            }.catch { e ->
                Log.e(TAG, "Erreur dans le flow de combinaison du profil", e)
                emit(PublicProfileUiState(profileLoadState = ProfileLoadState.Error("Erreur technique: ${e.localizedMessage}")))
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun toggleLikeOnCurrentReading() {
        val bookId = uiState.value.readingExperience?.book?.id ?: return
        if (currentUserId.isNullOrBlank()) return

        viewModelScope.launch {
            val result = socialRepository.toggleLikeOnBook(bookId, currentUserId)
            if (result is Resource.Error) {
                _userInteractionEvents.emit(result.message ?: "Erreur inconnue")
            }
        }
    }

    fun postCommentOnCurrentReading(commentText: String) {
        val experience = uiState.value.readingExperience ?: return
        val currentUser = firebaseAuth.currentUser ?: return
        if (commentText.isBlank()) {
            viewModelScope.launch { _userInteractionEvents.emit("Le commentaire ne peut pas être vide.") }
            return
        }

        val comment = Comment(
            userId = currentUser.uid,
            userName = currentUser.displayName ?: "Anonyme",
            userPhotoUrl = currentUser.photoUrl?.toString(),
            targetUserId = targetUserId,
            bookId = experience.book.id,
            commentText = commentText.trim()
        )
        viewModelScope.launch {
            val result = socialRepository.addCommentOnBook(experience.book.id, comment)
            if (result is Resource.Error) {
                _userInteractionEvents.emit(result.message ?: "Erreur inconnue")
            }
        }
    }

    fun deleteComment(comment: Comment) {
        val experience = uiState.value.readingExperience ?: return
        viewModelScope.launch {
            val result = socialRepository.deleteCommentOnBook(experience.book.id, comment.commentId)
            if(result is Resource.Error) _userInteractionEvents.emit(result.message ?: "Erreur suppression")
        }
    }

    fun toggleLikeOnComment(comment: Comment) {
        val experience = uiState.value.readingExperience ?: return
        val uid = currentUserId ?: return
        viewModelScope.launch {
            val result = socialRepository.toggleLikeOnComment(experience.book.id, comment.commentId, uid)
            if(result is Resource.Error) _userInteractionEvents.emit(result.message ?: "Erreur like")
        }
    }

    fun getCommentLikeStatus(commentId: String): Flow<Resource<Boolean>> {
        val experience = uiState.value.readingExperience
        if(experience == null || currentUserId == null) return flowOf(Resource.Error("Info manquante"))
        return socialRepository.isCommentLikedByCurrentUser(experience.book.id, commentId, currentUserId)
    }

    fun toggleFollowStatus() {
        if (currentUserId.isNullOrBlank() || currentUserId == targetUserId) return
        viewModelScope.launch {
            val result = if (uiState.value.isFollowing) {
                socialRepository.unfollowUser(currentUserId, targetUserId)
            } else {
                socialRepository.followUser(currentUserId, targetUserId)
            }
            if (result is Resource.Error) {
                Log.e(TAG, "Erreur lors du toggleFollow: ${result.message}")
            }
        }
    }
}