// PRÊT À COLLER - Fichier 100% complet et corrigé
package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
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

data class CurrentReadingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bookReading: UserBookReading? = null,
    val bookDetails: Book? = null,
    val isOwnedProfile: Boolean = false
)

sealed class CommentEvent {
    data class ShowCommentError(val message: String) : CommentEvent()
    data object ClearCommentInput : CommentEvent()
    data class CommentDeletedSuccess(val commentId: String) : CommentEvent()
    data class ShowCommentLikeError(val message: String) : CommentEvent()
}

sealed class LikeEvent {
    data class ShowLikeError(val message: String) : LikeEvent()
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PublicProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val bookRepository: BookRepository,
    private val socialRepository: SocialRepository,
    private val readingRepository: ReadingRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PublicProfileViewModel"
    }

    private val _userProfile = MutableLiveData<Resource<User>>()
    val userProfile: LiveData<Resource<User>> = _userProfile

    private val userIdFromArgs: String? = savedStateHandle.get<String>("userId")

    private val _currentUserId = MutableLiveData<String?>(firebaseAuth.currentUser?.uid)
    val currentUserId: LiveData<String?> = _currentUserId

    private val _isFollowing = MutableStateFlow<Resource<Boolean>>(Resource.Loading())
    val isFollowing: StateFlow<Resource<Boolean>> = _isFollowing.asStateFlow()

    private val _isFollowedByTarget = MutableStateFlow<Resource<Boolean>>(Resource.Loading())
    private val _isMutualFollow = MutableStateFlow<Resource<Boolean>>(Resource.Loading())
    val isMutualFollow: StateFlow<Resource<Boolean>> = _isMutualFollow.asStateFlow()

    private val _currentReadingUiState = MutableStateFlow(CurrentReadingUiState(isLoading = true))
    val currentReadingUiState: StateFlow<CurrentReadingUiState> = _currentReadingUiState.asStateFlow()

    private val _bookIdForInteractions = MutableStateFlow<String?>(null)

    private val _comments = MutableStateFlow<Resource<List<Comment>>>(Resource.Loading())
    val comments: StateFlow<Resource<List<Comment>>> = _comments.asStateFlow()

    private val _currentUserProfileForCommenting = MutableStateFlow<Resource<User>>(Resource.Loading())

    private val _commentEvents = MutableSharedFlow<CommentEvent>()
    val commentEvents: SharedFlow<CommentEvent> = _commentEvents.asSharedFlow()

    private val _isLikedByCurrentUser = MutableStateFlow<Resource<Boolean>>(Resource.Loading())
    val isLikedByCurrentUser: StateFlow<Resource<Boolean>> = _isLikedByCurrentUser.asStateFlow()

    private val _likesCount = MutableStateFlow<Resource<Int>>(Resource.Loading())
    val likesCount: StateFlow<Resource<Int>> = _likesCount.asStateFlow()

    private val _likeEvents = MutableSharedFlow<LikeEvent>()
    val likeEvents: SharedFlow<LikeEvent> = _likeEvents.asSharedFlow()

    init {
        val targetUserId = userIdFromArgs
        val currentAuthUserId = firebaseAuth.currentUser?.uid

        if (!targetUserId.isNullOrBlank()) {
            val isProfileOwner = targetUserId == currentAuthUserId
            fetchUserProfile(targetUserId)

            viewModelScope.launch {
                _currentReadingUiState.value = CurrentReadingUiState(isLoading = true, isOwnedProfile = isProfileOwner)
                readingRepository.getCurrentReading(targetUserId)
                    .flatMapLatest { readingResource ->
                        val userBookReading = (readingResource as? Resource.Success)?.data
                        if (userBookReading != null) {
                            bookRepository.getBookById(userBookReading.bookId)
                                .map { bookResource ->
                                    CurrentReadingUiState(
                                        isLoading = bookResource is Resource.Loading,
                                        bookReading = userBookReading,
                                        bookDetails = (bookResource as? Resource.Success)?.data,
                                        error = (bookResource as? Resource.Error)?.message,
                                        isOwnedProfile = isProfileOwner
                                    )
                                }
                        } else {
                            flowOf(CurrentReadingUiState(isLoading = false, isOwnedProfile = isProfileOwner))
                        }
                    }
                    .catch { e -> emit(CurrentReadingUiState(isLoading = false, error = e.localizedMessage, isOwnedProfile = isProfileOwner)) }
                    .collectLatest { _currentReadingUiState.value = it }
            }

            viewModelScope.launch {
                _currentReadingUiState.map { it.bookReading?.bookId }.distinctUntilChanged().collectLatest { bookId ->
                    _bookIdForInteractions.value = bookId
                }
            }

            observeSocialInteractions(currentAuthUserId)

            if (!currentAuthUserId.isNullOrBlank() && !isProfileOwner) {
                observeFollowingStatus(currentAuthUserId, targetUserId)
                observeFollowedByTargetStatus(targetUserId, currentAuthUserId)
                combineFollowStatus()
            } else {
                _isFollowing.value = Resource.Success(false)
                _isFollowedByTarget.value = Resource.Success(false)
                _isMutualFollow.value = Resource.Success(false)
            }
        } else {
            _userProfile.value = Resource.Error("ID utilisateur manquant pour charger le profil.")
        }
    }

    private fun observeSocialInteractions(currentAuthUserId: String?) {
        viewModelScope.launch {
            _bookIdForInteractions.filterNotNull().flatMapLatest { bookId ->
                socialRepository.getCommentsForBook(bookId)
            }.collectLatest { _comments.value = it }
        }

        if (!currentAuthUserId.isNullOrBlank()) {
            viewModelScope.launch {
                userProfileRepository.getUserById(currentAuthUserId).collectLatest {
                    _currentUserProfileForCommenting.value = it
                }
            }

            viewModelScope.launch {
                _bookIdForInteractions.filterNotNull().flatMapLatest { bookId ->
                    socialRepository.isBookLikedByUser(bookId, currentAuthUserId)
                }.collectLatest { _isLikedByCurrentUser.value = it }
            }

            viewModelScope.launch {
                _bookIdForInteractions.filterNotNull().flatMapLatest { bookId ->
                    socialRepository.getBookLikesCount(bookId)
                }.collectLatest { _likesCount.value = it }
            }
        }
    }

    private fun combineFollowStatus() {
        viewModelScope.launch {
            _isFollowing.combine(_isFollowedByTarget) { isAFollowsB, isBFollowsA ->
                if (isAFollowsB is Resource.Success && isBFollowsA is Resource.Success) {
                    Resource.Success(isAFollowsB.data == true && isBFollowsA.data == true)
                } else if (isAFollowsB is Resource.Error) {
                    isAFollowsB
                } else if (isBFollowsA is Resource.Error) {
                    isBFollowsA
                } else {
                    Resource.Loading()
                }
            }.catch { e ->
                _isMutualFollow.value = Resource.Error(e.localizedMessage ?: "Erreur de combinaison")
            }.collectLatest {
                _isMutualFollow.value = it
            }
        }
    }

    private fun fetchUserProfile(id: String) {
        viewModelScope.launch {
            _userProfile.value = Resource.Loading()
            userProfileRepository.getUserById(id)
                .catch { e -> _userProfile.postValue(Resource.Error("Erreur technique: ${e.localizedMessage}")) }
                .collectLatest { _userProfile.value = it }
        }
    }

    private fun observeFollowingStatus(currentUserId: String, targetUserId: String) {
        viewModelScope.launch {
            socialRepository.isFollowing(currentUserId, targetUserId)
                .catch { e -> _isFollowing.value = Resource.Error(e.localizedMessage ?: "Erreur") }
                .collectLatest { _isFollowing.value = it }
        }
    }

    private fun observeFollowedByTargetStatus(targetUserId: String, currentUserId: String) {
        viewModelScope.launch {
            socialRepository.isFollowing(targetUserId, currentUserId)
                .catch { e -> _isFollowedByTarget.value = Resource.Error(e.localizedMessage ?: "Erreur") }
                .collectLatest { _isFollowedByTarget.value = it }
        }
    }

    fun toggleFollowStatus() {
        val currentUserUid = firebaseAuth.currentUser?.uid
        val targetId = userIdFromArgs

        if (currentUserUid.isNullOrBlank() || targetId.isNullOrBlank() || currentUserUid == targetId) {
            viewModelScope.launch { _likeEvents.emit(LikeEvent.ShowLikeError("Action impossible.")) }
            return
        }

        viewModelScope.launch {
            val result = if (_isFollowing.value.data == true) {
                socialRepository.unfollowUser(currentUserUid, targetId)
            } else {
                socialRepository.followUser(currentUserUid, targetId)
            }
            if (result is Resource.Error) {
                Log.e(TAG, "Erreur lors du toggleFollow: ${result.message}")
            }
        }
    }

    fun postComment(commentText: String) {
        val bookId = _bookIdForInteractions.value
        val currentUser = (_currentUserProfileForCommenting.value as? Resource.Success)?.data
        val targetId = userIdFromArgs

        if (bookId.isNullOrBlank() || currentUser == null || commentText.isBlank() || targetId.isNullOrBlank()) {
            viewModelScope.launch { _commentEvents.emit(CommentEvent.ShowCommentError("Impossible de poster le commentaire.")) }
            return
        }

        val newComment = Comment(
            userId = currentUser.uid,
            userName = currentUser.username,
            userPhotoUrl = currentUser.profilePictureUrl,
            targetUserId = targetId,
            commentText = commentText.trim(),
            timestamp = Timestamp.now(),
            bookId = bookId
        )

        viewModelScope.launch {
            val result = socialRepository.addCommentOnBook(bookId, newComment)
            if (result is Resource.Success) {
                _commentEvents.emit(CommentEvent.ClearCommentInput)
            } else if (result is Resource.Error) {
                _commentEvents.emit(CommentEvent.ShowCommentError(result.message ?: "Erreur inconnue."))
            }
        }
    }

    // CORRIGÉ: La méthode accepte maintenant un objet Comment complet.
    fun deleteComment(comment: Comment) {
        val currentAuthUserId = firebaseAuth.currentUser?.uid
        val profileOwnerId = userIdFromArgs

        // CORRIGÉ: La logique de permission utilise maintenant les propriétés de l'objet comment.
        if (currentAuthUserId != comment.userId && currentAuthUserId != profileOwnerId) {
            viewModelScope.launch { _commentEvents.emit(CommentEvent.ShowCommentError("Action non autorisée.")) }
            return
        }

        viewModelScope.launch {
            // CORRIGÉ: Les arguments de la méthode sont maintenant extraits de l'objet comment.
            val result = socialRepository.deleteCommentOnBook(comment.bookId, comment.commentId)
            if (result is Resource.Success) {
                _commentEvents.emit(CommentEvent.CommentDeletedSuccess(comment.commentId))
            } else if (result is Resource.Error) {
                _commentEvents.emit(CommentEvent.ShowCommentError(result.message ?: "Erreur de suppression."))
            }
        }
    }

    fun toggleLike() {
        val bookId = _bookIdForInteractions.value
        val currentId = currentUserId.value
        if (bookId.isNullOrBlank() || currentId.isNullOrBlank()) return

        viewModelScope.launch {
            val result = socialRepository.toggleLikeOnBook(bookId, currentId)
            if (result is Resource.Error) {
                _likeEvents.emit(LikeEvent.ShowLikeError(result.message ?: "Erreur inconnue."))
            }
        }
    }

    fun toggleLikeOnComment(comment: Comment) {
        val currentId = currentUserId.value
        if (currentId.isNullOrBlank()) return

        viewModelScope.launch {
            val result = socialRepository.toggleLikeOnComment(comment.bookId, comment.commentId, currentId)
            if (result is Resource.Error) {
                _commentEvents.emit(CommentEvent.ShowCommentLikeError(result.message ?: "Erreur inconnue."))
            }
        }
    }

    fun getCommentLikeStatus(commentId: String): Flow<Resource<Boolean>> {
        val bookId = _bookIdForInteractions.value
        val currentId = currentUserId.value
        if (bookId.isNullOrBlank() || currentId.isNullOrBlank()) {
            return flowOf(Resource.Error("Données manquantes."))
        }
        return socialRepository.isCommentLikedByCurrentUser(bookId, commentId, currentId)
    }
}