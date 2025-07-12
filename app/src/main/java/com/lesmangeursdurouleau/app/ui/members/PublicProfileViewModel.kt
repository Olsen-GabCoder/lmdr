// Fichier complet : PublicProfileViewModel.kt

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
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
import com.lesmangeursdurouleau.app.data.repository.SocialRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
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
    private val readingRepository: ReadingRepository,
    private val bookRepository: BookRepository,
    private val localUserPreferencesRepository: LocalUserPreferencesRepository,
    private val firebaseAuth: FirebaseAuth,
    private val savedStateHandle: SavedStateHandle
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
                        is Resource.Success -> {
                            val count = commentsResource.data?.size ?: 0
                            _commentsState.value = CommentsState.ReadyToLoad(count)
                        }
                        is Resource.Error -> {
                            _commentsState.value = CommentsState.Error(commentsResource.message ?: "Erreur de chargement initial des commentaires")
                        }
                        else -> {
                            _commentsState.value = CommentsState.Error("État inattendu lors du décompte des commentaires")
                        }
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeFullCommentsStream() {
        viewModelScope.launch {
            _loadCommentsTrigger.filter { it }.flatMapLatest {
                val bookId = uiState.value.readingExperience?.book?.id
                if (bookId == null) {
                    return@flatMapLatest emptyFlow<CommentsState>()
                }

                socialRepository.getCommentsForReadingStream(targetUserId, bookId)
                    .combine(localUserPreferencesRepository.getHiddenCommentIds()) { commentsResource, hiddenIds ->
                        when (commentsResource) {
                            is Resource.Success -> {
                                val allComments = commentsResource.data ?: emptyList()
                                val uiComments = allComments.map { comment ->
                                    UiComment(
                                        comment = comment,
                                        isHidden = comment.commentId in hiddenIds,
                                        isHighlighted = comment.commentId == highlightedCommentId
                                    )
                                }
                                processComments(uiComments)
                            }
                            is Resource.Error -> CommentsState.Error(commentsResource.message ?: "Erreur inconnue")
                            is Resource.Loading -> _commentsState.value
                        }
                    }
            }.catch { e ->
                Log.e(TAG, "Erreur dans observeFullCommentsStream", e)
                emit(CommentsState.Error("Erreur technique: ${e.localizedMessage}"))
            }.collect {
                _commentsState.value = it
            }
        }
    }

    fun loadComments() {
        if (uiState.value.commentsState is CommentsState.ReadyToLoad) {
            _commentsState.value = CommentsState.LoadingInitial
            _loadCommentsTrigger.value = true
        }
    }

    fun onHighlightConsumed() {
        _uiState.update { it.copy(highlightedCommentId = null) }
    }

    private fun combineUiState() {
        viewModelScope.launch {
            val profileAndReadingFlow = createProfileAndReadingFlow()

            combine(
                profileAndReadingFlow,
                _replyingToComment,
                _commentsState
            ) { profileState, replyingTo, commentsState ->
                profileState.copy(
                    replyingToComment = replyingTo,
                    commentsState = commentsState
                )
            }.catch { e ->
                Log.e(TAG, "Erreur dans le flow de combinaison final", e)
            }.collect { newState ->
                _uiState.update { currentState ->
                    newState.copy(
                        mentionSuggestions = currentState.mentionSuggestions,
                        isSearchingMentions = currentState.isSearchingMentions,
                        highlightedCommentId = currentState.highlightedCommentId
                    )
                }
            }
        }
    }

    private fun observeMentionSearch() {
        viewModelScope.launch {
            _mentionQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.update { it.copy(mentionSuggestions = emptyList(), isSearchingMentions = false) }
                        return@collectLatest
                    }

                    _uiState.update { it.copy(isSearchingMentions = true) }
                    when (val result = socialRepository.searchUsersByUsername(query, MENTION_SEARCH_LIMIT)) {
                        is Resource.Success -> {
                            _uiState.update {
                                it.copy(
                                    mentionSuggestions = result.data ?: emptyList(),
                                    isSearchingMentions = false
                                )
                            }
                        }
                        is Resource.Error -> {
                            Log.e(TAG, "Erreur de recherche de mention: ${result.message}")
                            _uiState.update { it.copy(isSearchingMentions = false) }
                        }
                        is Resource.Loading -> {}
                    }
                }
        }
    }

    private fun createProfileAndReadingFlow(): Flow<PublicProfileUiState> {
        val userProfileFlow = userProfileRepository.getUserById(targetUserId)
        val isFollowingFlow = if (currentUserId != null && currentUserId != targetUserId) {
            socialRepository.isFollowing(currentUserId, targetUserId)
        } else {
            flowOf(Resource.Success(false))
        }

        val readingExperienceFlow: Flow<Resource<CurrentReadingExperience?>> =
            readingRepository.getCurrentReading(targetUserId).flatMapLatest { readingResource ->
                val reading = (readingResource as? Resource.Success)?.data
                if (reading == null || reading.bookId.isBlank()) {
                    return@flatMapLatest flowOf(Resource.Success(null))
                }

                bookRepository.getBookById(reading.bookId)
                    .combine(socialRepository.getBookLikesCount(reading.bookId)) { bookRes, likesRes ->
                        Pair(bookRes, likesRes)
                    }.flatMapLatest { (bookRes, likesRes) ->
                        if (bookRes !is Resource.Success || bookRes.data == null) {
                            return@flatMapLatest flowOf(
                                if (bookRes is Resource.Error) Resource.Error(bookRes.message ?: "Erreur livre") else Resource.Loading()
                            )
                        }

                        val isLikedFlow = currentUserId?.let {
                            socialRepository.isReadingLikedByUser(targetUserId, reading.bookId, it)
                        } ?: flowOf(Resource.Success(false))

                        val isBookmarkedFlow = currentUserId?.let { socialRepository.isBookBookmarkedByUser(reading.bookId, it) } ?: flowOf(Resource.Success(false))
                        val userRatingFlow = currentUserId?.let { socialRepository.getUserRatingForBook(reading.bookId, it) } ?: flowOf(Resource.Success(null))
                        val isRecommendedFlow = currentUserId?.let { socialRepository.isBookRecommendedByUser(reading.bookId, it) } ?: flowOf(Resource.Success(false))

                        combine(isLikedFlow, isBookmarkedFlow, userRatingFlow, isRecommendedFlow) { isLiked, isBookmarked, userRating, isRecommended ->
                            Resource.Success(
                                CurrentReadingExperience(
                                    reading = reading,
                                    book = bookRes.data,
                                    likesCount = (likesRes as? Resource.Success)?.data ?: 0,
                                    isLikedByCurrentUser = (isLiked as? Resource.Success)?.data ?: false,
                                    isBookmarkedByCurrentUser = (isBookmarked as? Resource.Success)?.data ?: false,
                                    currentUserRating = (userRating as? Resource.Success)?.data,
                                    isRecommendedByCurrentUser = (isRecommended as? Resource.Success)?.data ?: false
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

    private fun processComments(uiComments: List<UiComment>): CommentsState {
        // JUSTIFICATION DE LA MODIFICATION : L'appel `.filterNot { it.isHidden }` a été supprimé.
        // C'était la cause racine de la régression fonctionnelle observée. En le retirant, nous nous assurons
        // que les commentaires marqués comme masqués sont bien inclus dans l'état final et transmis à l'adapter.
        // L'adapter peut ainsi utiliser sa logique `getItemViewType` pour afficher le placeholder correct,
        // restaurant ainsi la fonctionnalité requise par les spécifications.
        val (parents, replies) = uiComments
            .partition { it.comment.parentCommentId == null }

        val repliesMap = replies.groupBy { it.comment.parentCommentId!! }

        return CommentsState.Loaded(
            parentComments = parents,
            repliesMap = repliesMap
        )
    }

    fun shareComment(comment: Comment) {
        viewModelScope.launch {
            if (targetUserId.isBlank() || comment.commentId.isBlank()) {
                _events.emit(Event.ShowToast("Impossible de partager ce commentaire."))
                return@launch
            }
            val deepLink = "$DEEP_LINK_BASE_URI/comment?targetUserId=$targetUserId&commentId=${comment.commentId}"
            val shareText = "Regarde ce commentaire sur Les Mangeurs du Rouleau :\n$deepLink"
            _events.emit(Event.ShareContent(shareText))
        }
    }

    fun hideComment(commentId: String) {
        viewModelScope.launch {
            localUserPreferencesRepository.hideComment(commentId)
            _events.emit(Event.ShowToast("Commentaire masqué."))
        }
    }

    fun unhideComment(commentId: String) {
        viewModelScope.launch {
            localUserPreferencesRepository.unhideComment(commentId)
            _events.emit(Event.ShowToast("Commentaire affiché."))
        }
    }

    fun searchForMention(query: String) { _mentionQuery.value = query }
    fun clearMentionSuggestions() { _mentionQuery.value = ""
        _uiState.update { it.copy(mentionSuggestions = emptyList()) } }
    fun startReplyingTo(comment: Comment) { _replyingToComment.value = comment }
    fun cancelReply() { _replyingToComment.value = null }

    fun postCommentOnCurrentReading(commentText: String) {
        val experience = uiState.value.readingExperience ?: return
        val currentUser = firebaseAuth.currentUser ?: return
        if (commentText.isBlank()) { viewModelScope.launch { _events.emit(Event.ShowToast("Le commentaire ne peut pas être vide.")) }
            return }
        val parentComment = _replyingToComment.value
        val comment = Comment(userId = currentUser.uid, userName = currentUser.displayName ?: "Anonyme", userPhotoUrl = currentUser.photoUrl?.toString(), bookId = experience.book.id, commentText = commentText.trim(), parentCommentId = parentComment?.commentId)
        viewModelScope.launch {
            val result = socialRepository.addCommentOnReading(targetUserId, experience.book.id, comment)
            if (result is Resource.Error) {
                _events.emit(Event.ShowToast(result.message ?: "Erreur inconnue"))
            }
            cancelReply()
        }
    }

    fun updateComment(comment: Comment, newText: String) {
        if (newText.isBlank()) { viewModelScope.launch { _events.emit(Event.ShowToast("Le commentaire ne peut pas être vide.")) }
            return }
        viewModelScope.launch {
            val result = socialRepository.updateCommentOnReading(targetUserId, comment.bookId, comment.commentId, newText.trim())
            if (result is Resource.Success) {
                _events.emit(Event.ShowToast("Commentaire modifié avec succès."))
            } else if (result is Resource.Error) {
                _events.emit(Event.ShowToast(result.message ?: "Erreur lors de la modification."))
            }
        }
    }

    fun toggleLikeOnCurrentReading() {
        val bookId = uiState.value.readingExperience?.book?.id ?: return
        val likerId = currentUserId ?: return
        viewModelScope.launch { val result = socialRepository.toggleLikeOnReading(targetUserId, bookId, likerId)
            if (result is Resource.Error) { _events.emit(Event.ShowToast(result.message ?: "Erreur inconnue")) } } }

    fun toggleBookmarkOnCurrentReading() {
        val bookId = uiState.value.readingExperience?.book?.id ?: return
        if (currentUserId.isNullOrBlank()) return
        viewModelScope.launch { val result = socialRepository.toggleBookmarkOnBook(bookId, currentUserId)
            if (result is Resource.Error) { _events.emit(Event.ShowToast(result.message ?: "Erreur inconnue")) } } }

    fun rateCurrentReading(rating: Float) {
        val bookId = uiState.value.readingExperience?.book?.id ?: return
        if (currentUserId.isNullOrBlank()) return
        viewModelScope.launch { val result = socialRepository.rateBook(bookId, currentUserId, rating)
            if (result is Resource.Error) { _events.emit(Event.ShowToast(result.message ?: "Erreur lors de la notation")) } } }

    fun toggleRecommendationOnCurrentReading() {
        val bookId = uiState.value.readingExperience?.book?.id ?: return
        if (currentUserId.isNullOrBlank()) return
        viewModelScope.launch { val result = socialRepository.toggleRecommendationOnBook(bookId, currentUserId)
            if (result is Resource.Error) { _events.emit(Event.ShowToast(result.message ?: "Erreur lors de la recommandation")) } } }

    fun deleteComment(comment: Comment) {
        val experience = uiState.value.readingExperience ?: return
        viewModelScope.launch {
            val result = socialRepository.deleteCommentOnReading(targetUserId, experience.book.id, comment.commentId)
            if(result is Resource.Error) {
                _events.emit(Event.ShowToast(result.message ?: "Erreur suppression"))
            }
        }
    }

    fun toggleLikeOnComment(comment: Comment) {
        val experience = uiState.value.readingExperience ?: return
        val uid = currentUserId ?: return
        viewModelScope.launch {
            val result = socialRepository.toggleLikeOnCommentForReading(targetUserId, experience.book.id, comment.commentId, uid)
            if(result is Resource.Error) _events.emit(Event.ShowToast(result.message ?: "Erreur like")) } }

    fun reportComment(comment: Comment) {
        val reportingUserId = currentUserId ?: return
        val reason = "Signalement depuis le profil public."
        viewModelScope.launch {
            val result = socialRepository.reportCommentOnReading(targetUserId, comment.bookId, comment.commentId, reportingUserId, reason)
            val message = if (result is Resource.Success) { "Commentaire signalé. Merci de contribuer à la sécurité de la communauté."
            } else { (result as Resource.Error).message ?: "Une erreur est survenue lors du signalement." }
            _events.emit(Event.ShowToast(message))
        }
    }

    fun getCommentLikeStatus(commentId: String): Flow<Resource<Boolean>> {
        val experience = uiState.value.readingExperience
        if(experience == null || currentUserId == null) return flowOf(Resource.Error("Info manquante"))
        return socialRepository.isCommentLikedByUserOnReading(targetUserId, experience.book.id, commentId, currentUserId)
    }

    fun toggleFollowStatus() {
        if (currentUserId.isNullOrBlank() || currentUserId == targetUserId) return
        viewModelScope.launch { val result = if (uiState.value.isFollowing) { socialRepository.unfollowUser(currentUserId, targetUserId)
        } else { socialRepository.followUser(currentUserId, targetUserId) }
            if (result is Resource.Error) { Log.e(TAG, "Erreur lors du toggleFollow: ${result.message}") } } }
}