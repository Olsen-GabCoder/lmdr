// PRÊT À COLLER - Fichier 100% complet
package com.lesmangeursdurouleau.app.ui.members

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.*
import com.lesmangeursdurouleau.app.data.repository.ChallengesRepository
import com.lesmangeursdurouleau.app.data.repository.LeaderboardRepository
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ToolbarState(
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val userStatus: String = "",
    val affinityScoreText: String = "",
    @DrawableRes val affinityIconRes: Int? = null,
    val showAffinity: Boolean = false,
    val isStreakVisible: Boolean = false
)

sealed class ChatEvent {
    data class TierUpgrade(val message: String) : ChatEvent()
    data class ChallengeCompleted(val message: String) : ChatEvent()
}

@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val privateChatRepository: PrivateChatRepository,
    private val challengesRepository: ChallengesRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val firebaseAuth: FirebaseAuth,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TYPING_DEBOUNCE_MS = 500L
        private const val TYPING_TIMEOUT_MS = 3000L
        private const val TIER_1_THRESHOLD = 21
        private const val TIER_2_THRESHOLD = 51
        private const val TIER_3_THRESHOLD = 101
        private const val LEADERBOARD_LIMIT = 10L
    }
    private val textInputFlow = MutableStateFlow("")
    private var typingTimeoutJob: Job? = null
    private var isCurrentlyTyping = false

    private val targetUserId: String = savedStateHandle.get<String>("targetUserId")!!
    val conversationId = MutableStateFlow<String?>(null)

    private val _isChatVisible = MutableStateFlow(false)

    private val _messages = MutableStateFlow<Resource<List<PrivateMessage>>>(Resource.Loading())
    private val _targetUser = MutableStateFlow<Resource<User>>(Resource.Loading())
    val targetUser = _targetUser.asStateFlow()

    private val _currentUser = MutableStateFlow<Resource<User>>(Resource.Loading())
    val currentUser = _currentUser.asStateFlow()

    val conversation = MutableStateFlow<Conversation?>(null)

    private val _isTargetUserTyping = conversation.map {
        it?.typingStatus?.get(targetUserId) ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _replyingToMessage = MutableStateFlow<PrivateMessage?>(null)
    val replyingToMessage = _replyingToMessage.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events = _events.asSharedFlow()

    val weeklyChallenges: StateFlow<Resource<List<Challenge>>> =
        challengesRepository.getWeeklyChallenges()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )

    val challengeCompletionEvent = MutableSharedFlow<ChatEvent.ChallengeCompleted>()

    val leaderboardState: StateFlow<Resource<List<Conversation>>> =
        leaderboardRepository.getAffinityLeaderboard(LEADERBOARD_LIMIT)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Resource.Loading()
            )

    val toolbarState: StateFlow<ToolbarState> = combine(
        _targetUser,
        _isTargetUserTyping,
        conversation
    ) { userResource, isTyping, conv ->
        val user = (userResource as? Resource.Success)?.data
        val score = conv?.affinityScore ?: 0

        ToolbarState(
            userName = user?.username ?: "Chargement...",
            userPhotoUrl = user?.profilePictureUrl,
            userStatus = formatUserStatus(user, isTyping),
            affinityScoreText = score.toString(),
            affinityIconRes = getAffinityIcon(score),
            showAffinity = score > 0,
            isStreakVisible = conv?.isStreakActive ?: false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ToolbarState()
    )

    // === DÉBUT DE L'AJOUT QUI CORRIGE L'ERREUR ===
    /**
     * Expose un état de chargement combiné pour les données d'affinité.
     * Cette variable sera `true` tant que la conversation OU les défis n'auront pas été chargés avec succès.
     * Le Fragment l'observera pour activer/désactiver les boutons d'affinité.
     */
    val isAffinityDataLoading: StateFlow<Boolean> = combine(
        conversation,
        weeklyChallenges
    ) { conv, challengesResource ->
        // Les données sont considérées comme en cours de chargement si...
        conv == null || challengesResource !is Resource.Success
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true // On commence toujours en état de chargement.
    )
    // === FIN DE L'AJOUT QUI CORRIGE L'ERREUR ===

    @DrawableRes
    private fun getAffinityIcon(score: Int): Int {
        return when {
            score >= TIER_3_THRESHOLD -> R.drawable.ic_heart_fire
            score >= TIER_2_THRESHOLD -> R.drawable.ic_heart_red
            score >= TIER_1_THRESHOLD -> R.drawable.ic_heart_red
            else -> R.drawable.ic_heart_white
        }
    }

    val chatItems: StateFlow<Resource<List<ChatItem>>> = _messages.map { resource ->
        when (resource) {
            is Resource.Success -> {
                val processedList = addDateSeparators(resource.data ?: emptyList())
                Resource.Success(processedList)
            }
            is Resource.Error -> Resource.Error(resource.message ?: "Erreur inconnue")
            is Resource.Loading -> Resource.Loading()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Resource.Loading()
    )

    private val _sendState = MutableStateFlow<Resource<Unit>?>(null)
    val sendState = _sendState.asStateFlow()
    private val _deleteState = MutableStateFlow<Resource<Unit>?>(null)
    val deleteState = _deleteState.asStateFlow()
    private val _editState = MutableStateFlow<Resource<Unit>?>(null)
    val editState = _editState.asStateFlow()

    init {
        initializeConversation()
        loadUsers()
        observeTypingStatus()
    }

    fun triggerTierUpgradeEffect(message: String) {
        viewModelScope.launch {
            _events.emit(ChatEvent.TierUpgrade(message))
        }
    }

    fun getCurrentConversationState(): Conversation? {
        return conversation.value
    }

    fun completeChallenge(challenge: Challenge) {
        val convId = conversationId.value
        if (convId == null) {
            viewModelScope.launch {
                challengeCompletionEvent.emit(ChatEvent.ChallengeCompleted("Erreur : conversation non trouvée."))
            }
            return
        }

        viewModelScope.launch {
            val result = privateChatRepository.completeChallenge(convId, challenge.id, challenge.bonusPoints)
            if (result is Resource.Success) {
                challengeCompletionEvent.emit(ChatEvent.ChallengeCompleted("Défi relevé ! +${challenge.bonusPoints} points d'affinité !"))
            } else {
                challengeCompletionEvent.emit(ChatEvent.ChallengeCompleted(result.message ?: "Une erreur est survenue."))
            }
        }
    }

    fun setChatActive(isActive: Boolean) {
        _isChatVisible.value = isActive
        val convId = conversationId.value
        val userId = firebaseAuth.currentUser?.uid

        if (convId != null && userId != null) {
            viewModelScope.launch {
                privateChatRepository.updateUserActiveStatus(convId, userId, isActive)
            }
        }

        if (isActive && convId != null) {
            markConversationAsRead(convId)
        }
    }

    private fun loadUsers() {
        val currentUid = firebaseAuth.currentUser?.uid
        if (currentUid != null) {
            userProfileRepository.getUserById(currentUid).onEach { _currentUser.value = it }.launchIn(viewModelScope)
        }
        userProfileRepository.getUserById(targetUserId).onEach { _targetUser.value = it }.launchIn(viewModelScope)
    }

    private fun addDateSeparators(messages: List<PrivateMessage>): List<ChatItem> {
        if (messages.isEmpty()) return emptyList()
        val fullChatList = mutableListOf<ChatItem>()
        val calendar = Calendar.getInstance()
        messages.forEachIndexed { index, message ->
            val messageDate = message.timestamp ?: return@forEachIndexed
            val previousMessage = messages.getOrNull(index - 1)
            if (index == 0 || (previousMessage?.timestamp != null && !isSameDay(previousMessage.timestamp, messageDate, calendar))) {
                fullChatList.add(DateSeparatorItem(timestamp = messageDate))
            }
            fullChatList.add(MessageItem(message = message))
        }
        return fullChatList
    }

    private fun isSameDay(date1: Date, date2: Date, calendar: Calendar): Boolean {
        calendar.time = date1
        val year1 = calendar.get(Calendar.YEAR)
        val day1 = calendar.get(Calendar.DAY_OF_YEAR)
        calendar.time = date2
        val year2 = calendar.get(Calendar.YEAR)
        val day2 = calendar.get(Calendar.DAY_OF_YEAR)
        return year1 == year2 && day1 == day2
    }

    fun onReplyMessage(message: PrivateMessage) { _replyingToMessage.value = message }
    fun cancelReply() { _replyingToMessage.value = null }

    fun sendPrivateMessage(text: String) {
        val convId = conversationId.value
        val senderId = firebaseAuth.currentUser?.uid
        if (convId == null || senderId == null || text.isBlank()) return
        val replyInfo = _replyingToMessage.value?.let { originalMessage ->
            val originalSenderName = if (originalMessage.senderId == senderId) "Vous"
            else (_targetUser.value as? Resource.Success)?.data?.username ?: ""
            val preview = originalMessage.text ?: (if (originalMessage.imageUrl != null) "Image" else "Message")
            ReplyInfo(
                repliedToMessageId = originalMessage.id ?: "",
                repliedToSenderName = originalSenderName,
                repliedToMessagePreview = preview
            )
        }
        viewModelScope.launch {
            _sendState.value = Resource.Loading()
            setTypingStatus(false)
            val message = PrivateMessage(senderId = senderId, text = text, replyInfo = replyInfo)
            val result = privateChatRepository.sendPrivateMessage(convId, message)
            _sendState.value = result
            if (result is Resource.Success) {
                cancelReply()
            }
        }
    }

    private fun formatUserStatus(user: User?, isTyping: Boolean): String {
        if (isTyping) return "est en train d'écrire..."
        if (user?.isOnline == true) return "en ligne"
        val lastSeenDate = user?.lastSeen ?: return ""
        val now = System.currentTimeMillis()
        val diff = now - lastSeenDate.time
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "Dernière activité à l'instant"
            minutes < 60 -> "Dernière activité il y a $minutes min"
            hours < 24 -> "Dernière activité il y a $hours h"
            days < 2 -> "Dernière activité hier"
            else -> {
                val format = SimpleDateFormat("dd/MM/yy", Locale.FRENCH)
                "Dernière activité le ${format.format(lastSeenDate)}"
            }
        }
    }

    private fun initializeConversation() {
        viewModelScope.launch {
            val currentUserId = firebaseAuth.currentUser?.uid ?: return@launch
            _messages.value = Resource.Loading()
            val resource = privateChatRepository.createOrGetConversation(currentUserId, targetUserId)

            if (resource is Resource.Success && resource.data != null) {
                val convId = resource.data
                conversationId.value = convId

                markConversationAsRead(convId)

                loadMessages(convId)
                observeConversation(convId)
            } else {
                val errorMessage = (resource as? Resource.Error)?.message ?: "Impossible de trouver ou de créer la conversation."
                _messages.value = Resource.Error(errorMessage)
            }
        }
    }

    private fun observeConversation(conversationId: String) {
        privateChatRepository.getConversation(conversationId).onEach { resource ->
            when (resource) {
                is Resource.Success -> conversation.value = resource.data
                is Resource.Error -> Log.e("PrivateChatVM", "Erreur observation conversation: ${resource.message}")
                is Resource.Loading -> { /* Gérer l'état de chargement */ }
            }
        }.launchIn(viewModelScope)
    }

    fun formatDateLabel(date: Date, context: Context): String {
        val calendar = Calendar.getInstance()
        val today = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = calendar.time
        val targetCalendar = Calendar.getInstance().apply { time = date }
        return when {
            isSameDay(date, today, Calendar.getInstance()) -> "AUJOURD'HUI"
            isSameDay(date, yesterday, Calendar.getInstance()) -> "HIER"
            else -> {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val targetYear = targetCalendar.get(Calendar.YEAR)
                val format = if (currentYear == targetYear) SimpleDateFormat("d MMMM", Locale.FRENCH)
                else SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
                format.format(date).uppercase(Locale.FRENCH)
            }
        }
    }

    fun onUserTyping(text: String) { textInputFlow.value = text }

    private fun observeTypingStatus() {
        textInputFlow.debounce(TYPING_DEBOUNCE_MS).onEach {
            typingTimeoutJob?.cancel()
            val currentUserId = firebaseAuth.currentUser?.uid
            val convId = conversationId.value
            if (currentUserId == null || convId == null) return@onEach
            if (it.isNotBlank() && !isCurrentlyTyping) {
                isCurrentlyTyping = true
                privateChatRepository.updateTypingStatus(convId, currentUserId, true)
            }
            if (it.isBlank() && isCurrentlyTyping) {
                setTypingStatus(false)
            } else if (it.isNotBlank()) {
                typingTimeoutJob = viewModelScope.launch {
                    delay(TYPING_TIMEOUT_MS)
                    setTypingStatus(false)
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun setTypingStatus(isTyping: Boolean) {
        typingTimeoutJob?.cancel()
        val currentUserId = firebaseAuth.currentUser?.uid
        val convId = conversationId.value
        if (currentUserId != null && convId != null && isCurrentlyTyping != isTyping) {
            isCurrentlyTyping = isTyping
            viewModelScope.launch { privateChatRepository.updateTypingStatus(convId, currentUserId, isTyping) }
        } else if (isCurrentlyTyping != isTyping) {
            isCurrentlyTyping = isTyping
        }
    }

    override fun onCleared() {
        super.onCleared()
        setChatActive(false)
        setTypingStatus(false)
        cancelReply()
    }

    private fun markConversationAsRead(conversationId: String) {
        viewModelScope.launch {
            val result = privateChatRepository.markConversationAsRead(conversationId)
            if (result is Resource.Error) {
                Log.e("PrivateChatVM", "Erreur lors du marquage de la conversation comme lue: ${result.message}")
            } else {
                Log.d("PrivateChatVM", "Appel à markConversationAsRead pour $conversationId soumis.")
            }
        }
    }

    private fun loadMessages(conversationId: String) {
        privateChatRepository.getConversationMessages(conversationId)
            .onEach { resource ->
                _messages.value = resource

                if (resource is Resource.Success) {
                    val receivedUnread = resource.data?.any {
                        it.senderId != firebaseAuth.currentUser?.uid && it.status == MessageStatus.SENT.name
                    } == true

                    if (_isChatVisible.value && receivedUnread) {
                        markConversationAsRead(conversationId)
                    }
                }
            }.launchIn(viewModelScope)
    }

    fun sendImageMessage(uri: Uri) {
        val convId = conversationId.value
        if (convId == null) {
            _sendState.value = Resource.Error("ID de conversation non disponible")
            return
        }
        viewModelScope.launch {
            _sendState.value = Resource.Loading()
            setTypingStatus(false)
            _sendState.value = privateChatRepository.sendImageMessage(convId, uri, null)
        }
    }

    fun deleteMessage(messageId: String) {
        val convId = conversationId.value
        if (convId == null || messageId.isBlank()) return
        viewModelScope.launch {
            _deleteState.value = Resource.Loading()
            _deleteState.value = privateChatRepository.deletePrivateMessage(convId, messageId)
        }
    }

    fun resetDeleteState() { _deleteState.value = null }

    fun addOrUpdateReaction(messageId: String, emoji: String) {
        val convId = conversationId.value
        val uid = firebaseAuth.currentUser?.uid
        if (convId == null || uid == null) return
        viewModelScope.launch {
            privateChatRepository.addOrUpdateReaction(convId, messageId, uid, emoji)
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val convId = conversationId.value
        if (convId == null || messageId.isBlank() || newText.isBlank()) return
        viewModelScope.launch {
            _editState.value = Resource.Loading()
            _editState.value = privateChatRepository.editPrivateMessage(convId, messageId, newText)
        }
    }

    fun resetEditState() { _editState.value = null }
}