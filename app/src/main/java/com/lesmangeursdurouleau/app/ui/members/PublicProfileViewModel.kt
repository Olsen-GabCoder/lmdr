// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PublicProfileViewModel.kt
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
import com.lesmangeursdurouleau.app.data.repository.LocalUserPreferencesRepository
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.domain.usecase.library.GetCurrentlyReadingEntryUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiComment(
    val comment: Comment,
    val isHidden: Boolean,
    val isHighlighted: Boolean = false
)

data class CurrentReadingExperience(
    val reading: UserBookReading,
    val book: Book,
    val likesCount: Int = 0,
    val isLikedByCurrentUser: Boolean = false,
    val isBookmarkedByCurrentUser: Boolean = false,
    val currentUserRating: Float? = null,
    val isRecommendedByCurrentUser: Boolean = false
)

sealed interface CommentsState {
    object NotLoadedYet : CommentsState
    data class ReadyToLoad(val count: Int) : CommentsState
    object LoadingInitial : CommentsState
    data class Loaded(
        val parentComments: List<UiComment>,
        val repliesMap: Map<String, List<UiComment>>
    ) : CommentsState
    data class Error(val message: String) : CommentsState
}

data class PublicProfileUiState(
    val profileLoadState: ProfileLoadState = ProfileLoadState.Loading,
    val user: User? = null,
    val isFollowing: Boolean = false,
    val isOwnedProfile: Boolean = false,
    val readingExperience: CurrentReadingExperience? = null,
    val commentsState: CommentsState = CommentsState.NotLoadedYet,
    val replyingToComment: Comment? = null,
    val mentionSuggestions: List<User> = emptyList(),
    val isSearchingMentions: Boolean = false,
    val highlightedCommentId: String? = null
)

sealed class ProfileLoadState {
    object Loading : ProfileLoadState()
    object Success : ProfileLoadState()
    data class Error(val message: String) : ProfileLoadState()
}

sealed class Event {
    data class ShowToast(val message: String) : Event()
    data class ShareContent(val text: String) : Event()
}


@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val socialRepository: SocialRepository,
    private val bookRepository: BookRepository,
    private val localUserPreferencesRepository: LocalUserPreferencesRepository,
    private val getCurrentlyReadingEntryUseCase: GetCurrentlyReadingEntryUseCase,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PublicProfileViewModel"
        private const val MENTION_SEARCH_LIMIT = 5L
        private const val DEEP_LINK_BASE_URI = "lmr://lesmangeursdurouleau.com"
    }

    private val targetUserId: String = savedStateHandle.get<String>("userId")!!
    private val highlightedCommentId: String? = savedStateHandle.get<String>("highlightedCommentId")
    val currentUserId: String? = firebaseAuth.currentUser?.uid

    private val _replyingToComment = MutableStateFlow<Comment?>(null)
    private val _mentionQuery = MutableStateFlow("")

    private val _commentsState = MutableStateFlow<CommentsState>(CommentsState.NotLoadedYet)
    private val _loadCommentsTrigger = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(PublicProfileUiState(highlightedCommentId = highlightedCommentId))
    val uiState: StateFlow<PublicProfileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    init {
        combineUiState()
        observeMentionSearch()
        setupInitialCommentsState()
        observeFullCommentsStream()
    }

    private fun createProfileAndReadingFlow(): Flow<PublicProfileUiState> {
        val userProfileFlow = userProfileRepository.getUserById(targetUserId)
        val isFollowingFlow = if (currentUserId != null && currentUserId != targetUserId) {
            socialRepository.isFollowing(currentUserId, targetUserId)
        } else {
            flowOf(Resource.Success(false))
        }

        val readingExperienceFlow: Flow<Resource<CurrentReadingExperience?>> =
            getCurrentlyReadingEntryUseCase(targetUserId).flatMapLatest { entryResource ->
                val entry = (entryResource as? Resource.Success)?.data
                if (entry == null || entry.bookId.isBlank()) {
                    return@flatMapLatest flowOf(Resource.Success(null))
                }

                bookRepository.getBookById(entry.bookId)
                    .combine(socialRepository.getBookLikesCount(entry.bookId)) { bookRes, likesRes ->
                        Pair(bookRes, likesRes)
                    }.flatMapLatest { (bookRes, likesRes) ->
                        if (bookRes !is Resource.Success || bookRes.data == null) {
                            val errorMessage = (bookRes as? Resource.Error)?.message ?: "Erreur livre"
                            return@flatMapLatest flowOf(Resource.Error(errorMessage))
                        }

                        val uid = currentUserId
                        val isLikedFlow = if (uid != null) socialRepository.isReadingLikedByUser(targetUserId, entry.bookId, uid) else flowOf(Resource.Success(false))
                        val isBookmarkedFlow = if (uid != null) socialRepository.isBookBookmarkedByUser(entry.bookId, uid) else flowOf(Resource.Success(false))
                        val userRatingFlow = if (uid != null) socialRepository.getUserRatingForBook(entry.bookId, uid) else flowOf(Resource.Success(null))
                        val isRecommendedFlow = if (uid != null) socialRepository.isBookRecommendedByUser(entry.bookId, uid) else flowOf(Resource.Success(false))

                        combine(
                            isLikedFlow,
                            isBookmarkedFlow,
                            userRatingFlow,
                            isRecommendedFlow
                        ) { isLiked, isBookmarked, userRating, isRecommended ->
                            val resources = listOf(isLiked, isBookmarked, userRating, isRecommended)
                            resources.firstOrNull { it is Resource.Error }?.let {
                                return@combine Resource.Error((it as Resource.Error).message ?: "Erreur sociale")
                            }
                            if (resources.any { it is Resource.Loading }) {
                                return@combine Resource.Loading()
                            }

                            val legacyReadingModel = UserBookReading(
                                bookId = entry.bookId,
                                title = bookRes.data.title,
                                author = bookRes.data.author,
                                coverImageUrl = bookRes.data.coverImageUrl,
                                currentPage = entry.currentPage,
                                totalPages = entry.totalPages,
                                startedReadingAt = entry.addedDate?.seconds,
                                lastPageUpdateAt = entry.lastReadDate?.seconds,
                                // AJOUT : On passe la citation ET la note personnelle au modèle legacy.
                                favoriteQuote = entry.favoriteQuote,
                                personalReflection = entry.personalReflection
                            )

                            Resource.Success(
                                CurrentReadingExperience(
                                    reading = legacyReadingModel,
                                    book = bookRes.data,
                                    likesCount = (likesRes as? Resource.Success)?.data ?: 0,
                                    isLikedByCurrentUser = (isLiked as Resource.Success).data ?: false,
                                    isBookmarkedByCurrentUser = (isBookmarked as Resource.Success).data ?: false,
                                    currentUserRating = (userRating as Resource.Success).data,
                                    isRecommendedByCurrentUser = (isRecommended as Resource.Success).data ?: false
                                )
                            )
                        }
                    }
            }

        return combine(userProfileFlow, isFollowingFlow, readingExperienceFlow) { user, isFollowing, readingExperience ->
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
        }
    }

    private fun setupInitialCommentsState() {
        viewModelScope.launch {
            uiState.mapNotNull { it.readingExperience?.book?.id }
                .distinctUntilChanged()
                .flatMapLatest { bookId ->
                    _loadCommentsTrigger.value = false
                    _commentsState.value = CommentsState.NotLoadedYet
                    socialRepository.getCommentsForReadingStream(targetUserId, bookId)
                }.first { it !is Resource.Loading }
                .let { commentsResource ->
                    when (commentsResource) {
                        is Resource.Success -> _commentsState.value = CommentsState.ReadyToLoad(commentsResource.data?.size ?: 0)
                        is Resource.Error -> _commentsState.value = CommentsState.Error(commentsResource.message ?: "Erreur")
                        else -> _commentsState.value = CommentsState.Error("État inattendu")
                    }
                }
        }
    }

    private fun observeFullCommentsStream() {
        viewModelScope.launch {
            _loadCommentsTrigger.filter { it }.flatMapLatest {
                val bookId = uiState.value.readingExperience?.book?.id ?: return@flatMapLatest emptyFlow<CommentsState>()
                socialRepository.getCommentsForReadingStream(targetUserId, bookId)
                    .combine(localUserPreferencesRepository.getHiddenCommentIds()) { commentsResource, hiddenIds ->
                        when (commentsResource) {
                            is Resource.Success -> {
                                val uiComments = (commentsResource.data ?: emptyList()).map { comment ->
                                    UiComment(comment, comment.commentId in hiddenIds, comment.commentId == highlightedCommentId)
                                }
                                processComments(uiComments)
                            }
                            is Resource.Error -> CommentsState.Error(commentsResource.message ?: "Erreur")
                            is Resource.Loading -> _commentsState.value
                        }
                    }
            }.catch { e -> emit(CommentsState.Error("Erreur: ${e.localizedMessage}")) }.collect { _commentsState.value = it }
        }
    }

    fun loadComments() { if (uiState.value.commentsState is CommentsState.ReadyToLoad) { _commentsState.value = CommentsState.LoadingInitial; _loadCommentsTrigger.value = true } }
    fun onHighlightConsumed() { _uiState.update { it.copy(highlightedCommentId = null) } }

    private fun combineUiState() {
        viewModelScope.launch {
            createProfileAndReadingFlow().combine(_replyingToComment) { profileState, replyingTo ->
                profileState.copy(replyingToComment = replyingTo)
            }.combine(_commentsState) { profileState, commentsState ->
                profileState.copy(commentsState = commentsState)
            }.catch { e -> Log.e(TAG, "Erreur dans le flow de combinaison final", e) }.collect { newState ->
                _uiState.update { it.copy(
                    profileLoadState = newState.profileLoadState, user = newState.user, isFollowing = newState.isFollowing,
                    isOwnedProfile = newState.isOwnedProfile, readingExperience = newState.readingExperience,
                    commentsState = newState.commentsState, replyingToComment = newState.replyingToComment
                )}
            }
        }
    }

    private fun observeMentionSearch() {
        viewModelScope.launch {
            _mentionQuery.debounce(300).distinctUntilChanged().collectLatest { query ->
                if (query.isBlank()) {
                    _uiState.update { it.copy(mentionSuggestions = emptyList(), isSearchingMentions = false) }
                    return@collectLatest
                }
                _uiState.update { it.copy(isSearchingMentions = true) }
                when (val result = socialRepository.searchUsersByUsername(query, MENTION_SEARCH_LIMIT)) {
                    is Resource.Success -> _uiState.update { it.copy(mentionSuggestions = result.data ?: emptyList(), isSearchingMentions = false) }
                    is Resource.Error -> { Log.e(TAG, "Erreur recherche mention: ${result.message}"); _uiState.update { it.copy(isSearchingMentions = false) } }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    private fun processComments(uiComments: List<UiComment>): CommentsState {
        val (parents, replies) = uiComments.partition { it.comment.parentCommentId == null }
        return CommentsState.Loaded(parents, replies.groupBy { it.comment.parentCommentId!! })
    }

    fun shareComment(comment: Comment) { viewModelScope.launch {
        if (targetUserId.isBlank() || comment.commentId.isBlank()) { _events.emit(Event.ShowToast("Impossible de partager")); return@launch }
        _events.emit(Event.ShareContent("Regarde ce commentaire sur Les Mangeurs du Rouleau :\n$DEEP_LINK_BASE_URI/comment?targetUserId=$targetUserId&commentId=${comment.commentId}"))
    }}
    fun hideComment(commentId: String) { viewModelScope.launch { localUserPreferencesRepository.hideComment(commentId); _events.emit(Event.ShowToast("Commentaire masqué.")) } }
    fun unhideComment(commentId: String) { viewModelScope.launch { localUserPreferencesRepository.unhideComment(commentId); _events.emit(Event.ShowToast("Commentaire affiché.")) } }
    fun searchForMention(query: String) { _mentionQuery.value = query }
    fun clearMentionSuggestions() { _mentionQuery.value = ""; _uiState.update { it.copy(mentionSuggestions = emptyList()) } }
    fun startReplyingTo(comment: Comment) { _replyingToComment.value = comment }
    fun cancelReply() { _replyingToComment.value = null }
    fun postCommentOnCurrentReading(commentText: String) {
        val experience = uiState.value.readingExperience ?: return; val currentUser = firebaseAuth.currentUser ?: return
        if (commentText.isBlank()) { viewModelScope.launch { _events.emit(Event.ShowToast("Le commentaire ne peut pas être vide.")) }; return }
        val parentComment = _replyingToComment.value
        val comment = Comment(userId = currentUser.uid, userName = currentUser.displayName ?: "Anonyme", userPhotoUrl = currentUser.photoUrl?.toString(), bookId = experience.book.id, commentText = commentText.trim(), parentCommentId = parentComment?.commentId)
        viewModelScope.launch { socialRepository.addCommentOnReading(targetUserId, experience.book.id, comment).let { if (it is Resource.Error) _events.emit(Event.ShowToast(it.message ?: "Erreur")) }; cancelReply() }
    }
    fun updateComment(comment: Comment, newText: String) {
        if (newText.isBlank()) { viewModelScope.launch { _events.emit(Event.ShowToast("Le commentaire ne peut pas être vide.")) }; return }
        viewModelScope.launch { socialRepository.updateCommentOnReading(targetUserId, comment.bookId, comment.commentId, newText.trim()).let { if (it is Resource.Success) _events.emit(Event.ShowToast("Commentaire modifié.")) else if(it is Resource.Error) _events.emit(Event.ShowToast(it.message ?: "Erreur")) } }
    }
    fun toggleLikeOnCurrentReading() {
        val bookId = uiState.value.readingExperience?.book?.id ?: return; val likerId = currentUserId ?: return
        viewModelScope.launch { socialRepository.toggleLikeOnReading(targetUserId, bookId, likerId).let { if (it is Resource.Error) _events.emit(Event.ShowToast(it.message ?: "Erreur")) } }
    }
    fun toggleBookmarkOnCurrentReading() {
        val bookId = uiState.value.readingExperience?.book?.id ?: return; if (currentUserId.isNullOrBlank()) return
        viewModelScope.launch { socialRepository.toggleBookmarkOnBook(bookId, currentUserId).let { if (it is Resource.Error) _events.emit(Event.ShowToast(it.message ?: "Erreur")) } }
    }
    fun rateCurrentReading(rating: Float) {
        val bookId = uiState.value.readingExperience?.book?.id ?: return; if (currentUserId.isNullOrBlank()) return
        viewModelScope.launch { socialRepository.rateBook(bookId, currentUserId, rating).let { if (it is Resource.Error) _events.emit(Event.ShowToast(it.message ?: "Erreur")) } }
    }
    fun toggleRecommendationOnCurrentReading() {
        val bookId = uiState.value.readingExperience?.book?.id ?: return; if (currentUserId.isNullOrBlank()) return
        viewModelScope.launch { socialRepository.toggleRecommendationOnBook(bookId, currentUserId).let { if (it is Resource.Error) _events.emit(Event.ShowToast(it.message ?: "Erreur")) } }
    }
    fun deleteComment(comment: Comment) {
        val experience = uiState.value.readingExperience ?: return
        viewModelScope.launch { socialRepository.deleteCommentOnReading(targetUserId, experience.book.id, comment.commentId).let { if (it is Resource.Error) _events.emit(Event.ShowToast(it.message ?: "Erreur")) } }
    }
    fun toggleLikeOnComment(comment: Comment) {
        val experience = uiState.value.readingExperience ?: return; val uid = currentUserId ?: return
        viewModelScope.launch { socialRepository.toggleLikeOnCommentForReading(targetUserId, experience.book.id, comment.commentId, uid).let { if (it is Resource.Error) _events.emit(Event.ShowToast(it.message ?: "Erreur")) } }
    }
    fun reportComment(comment: Comment) {
        val reportingUserId = currentUserId ?: return; val reason = "Signalement depuis le profil public."
        viewModelScope.launch {
            socialRepository.reportCommentOnReading(targetUserId, comment.bookId, comment.commentId, reportingUserId, reason).let {
                _events.emit(Event.ShowToast(if (it is Resource.Success) "Commentaire signalé." else (it as Resource.Error).message ?: "Erreur"))
            }
        }
    }
    fun getCommentLikeStatus(commentId: String): Flow<Resource<Boolean>> {
        val experience = uiState.value.readingExperience; if(experience == null || currentUserId == null) return flowOf(Resource.Error("Info manquante"))
        return socialRepository.isCommentLikedByUserOnReading(targetUserId, experience.book.id, commentId, currentUserId)
    }
    fun toggleFollowStatus() {
        if (currentUserId.isNullOrBlank() || currentUserId == targetUserId) return
        viewModelScope.launch { if (uiState.value.isFollowing) socialRepository.unfollowUser(currentUserId, targetUserId) else socialRepository.followUser(currentUserId, targetUserId) }
    }
}