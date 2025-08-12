// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PrivateChatViewModel.kt
package com.lesmangeursdurouleau.app.ui.members

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
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
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
    data class PaginationError(val message: String) : ChatEvent()
}

@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val privateChatRepository: PrivateChatRepository,
    private val challengesRepository: ChallengesRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TYPING_DEBOUNCE_MS = 500L
        private const val TYPING_TIMEOUT_MS = 3000L
        private const val MESSAGES_PAGE_SIZE = 30
        private const val TIER_1_THRESHOLD = 21
        private const val TIER_2_THRESHOLD = 51
        private const val TIER_3_THRESHOLD = 101
    }

    private val targetUserId: String = savedStateHandle.get<String>("targetUserId")!!
    val conversationId = MutableStateFlow<String?>(null)

    private val _isLoadingInitialMessages = MutableStateFlow(true)
    private val _isLoadingMoreMessages = MutableStateFlow(false)
    private val _hasMoreMessagesToLoad = MutableStateFlow(true)
    private var lastVisibleMessageId: String? = null
    private var newMessagesListenerJob: Job? = null

    private val _messages = MutableStateFlow<List<PrivateMessage>>(emptyList())

    val chatItems: StateFlow<List<ChatItem>> = combine(
        _messages,
        _isLoadingMoreMessages
    ) { messages, isLoadingMore ->
        val processedList = addDateSeparators(messages)
        if (isLoadingMore) {
            listOf(LoadingIndicatorItem) + processedList
        } else {
            processedList
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoading: StateFlow<Boolean> = _isLoadingInitialMessages
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMoreMessages

    val hasMoreMessagesToLoad = _hasMoreMessagesToLoad.asStateFlow()

    private val _targetUser = MutableStateFlow<Resource<User>>(Resource.Loading())
    val targetUser = _targetUser.asStateFlow()
    private val _currentUser = MutableStateFlow<Resource<User>>(Resource.Loading())
    val currentUser = _currentUser.asStateFlow()
    val conversation = MutableStateFlow<Conversation?>(null)
    private val _isChatVisible = MutableStateFlow(false)
    private val _replyingToMessage = MutableStateFlow<PrivateMessage?>(null)
    val replyingToMessage = _replyingToMessage.asStateFlow()
    private val _events = MutableSharedFlow<ChatEvent>()
    val events = _events.asSharedFlow()
    private val _sendState = MutableStateFlow<Resource<Unit>?>(null)
    val sendState = _sendState.asStateFlow()
    private val _deleteState = MutableStateFlow<Resource<Unit>?>(null)
    val deleteState = _deleteState.asStateFlow()
    private val _editState = MutableStateFlow<Resource<Unit>?>(null)
    val editState = _editState.asStateFlow()

    val weeklyChallenges: StateFlow<Resource<List<Challenge>>> = challengesRepository.getWeeklyChallenges()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Resource.Loading())

    val isAffinityDataLoading: StateFlow<Boolean> = combine(conversation, weeklyChallenges) { conv, challengesResource ->
        conv == null || challengesResource !is Resource.Success
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _isTargetUserTyping = conversation.map { it?.typingStatus?.get(targetUserId) ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val toolbarState: StateFlow<ToolbarState> = combine(_targetUser, _isTargetUserTyping, conversation) { userResource, isTyping, conv ->
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ToolbarState())

    init {
        initializeAndLoadData()
    }

    fun triggerTierUpgradeEffect(message: String) {
        viewModelScope.launch {
            _events.emit(ChatEvent.TierUpgrade(message))
        }
    }

    private fun initializeAndLoadData() {
        viewModelScope.launch {
            val currentUserId = firebaseAuth.currentUser?.uid ?: return@launch
            val resource = privateChatRepository.createOrGetConversation(currentUserId, targetUserId)
            if (resource is Resource.Success && resource.data != null) {
                val convId = resource.data
                conversationId.value = convId
                loadInitialMessages(convId)
                observeConversation(convId)
                loadUsers()
                observeTypingStatus()
            } else {
                _isLoadingInitialMessages.value = false
                val errorMsg = (resource as? Resource.Error)?.message ?: "Impossible de démarrer la conversation."
                _events.emit(ChatEvent.PaginationError(errorMsg))
            }
        }
    }

    private fun loadInitialMessages(convId: String) {
        viewModelScope.launch {
            _isLoadingInitialMessages.value = true
            val result = privateChatRepository.getConversationMessagesPaginated(convId, null, MESSAGES_PAGE_SIZE)
            when (result) {
                is Resource.Success -> {
                    result.data?.let { response ->
                        _hasMoreMessagesToLoad.value = response.hasMoreMessages
                        lastVisibleMessageId = response.lastVisibleMessageId
                        _messages.value = response.messages
                        listenForNewMessages(convId, response.messages.lastOrNull()?.timestamp)
                    }
                }
                is Resource.Error -> _events.emit(ChatEvent.PaginationError(result.message ?: "Erreur de chargement"))
                is Resource.Loading -> {}
            }
            _isLoadingInitialMessages.value = false
        }
    }

    fun loadMoreMessages() {
        if (_isLoadingMoreMessages.value || !_hasMoreMessagesToLoad.value) return
        val convId = conversationId.value ?: return
        viewModelScope.launch {
            _isLoadingMoreMessages.value = true
            val result = privateChatRepository.getConversationMessagesPaginated(convId, lastVisibleMessageId, MESSAGES_PAGE_SIZE)
            when (result) {
                is Resource.Success -> {
                    result.data?.let { response ->
                        _hasMoreMessagesToLoad.value = response.hasMoreMessages
                        lastVisibleMessageId = response.lastVisibleMessageId
                        val currentMessages = _messages.value
                        _messages.value = response.messages + currentMessages
                    }
                }
                is Resource.Error -> _events.emit(ChatEvent.PaginationError(result.message ?: "Erreur de chargement"))
                is Resource.Loading -> {}
            }
            _isLoadingMoreMessages.value = false
        }
    }

    private fun listenForNewMessages(conversationId: String, afterTimestamp: Date?) {
        newMessagesListenerJob?.cancel()
        newMessagesListenerJob = privateChatRepository.getConversationMessagesAfter(conversationId, afterTimestamp)
            .onEach { resource ->
                if (resource is Resource.Success) {
                    val newMessages = resource.data ?: emptyList()
                    if (newMessages.isNotEmpty()) {
                        val currentMessages = _messages.value
                        val updatedList = (currentMessages + newMessages)
                            .distinctBy { it.id }
                            .sortedBy { it.timestamp }
                        _messages.value = updatedList
                    }
                    if (_isChatVisible.value) {
                        markConversationAsRead(conversationId)
                    }
                }
            }.launchIn(viewModelScope)
    }

    fun sendPrivateMessage(text: String) {
        val convId = conversationId.value ?: return
        val senderId = firebaseAuth.currentUser?.uid ?: return
        if (text.isBlank()) return

        val replyInfo = _replyingToMessage.value?.let { originalMessage ->
            val originalSenderName = if (originalMessage.senderId == senderId) "Vous"
            else (_targetUser.value as? Resource.Success)?.data?.username ?: ""
            val preview = originalMessage.text ?: (if (originalMessage.imageUrl != null) "Image" else "Message")
            ReplyInfo(repliedToMessageId = originalMessage.id ?: "", repliedToSenderName = originalSenderName, repliedToMessagePreview = preview)
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

    private fun observeConversation(conversationId: String) {
        privateChatRepository.getConversation(conversationId).onEach { resource ->
            when (resource) {
                is Resource.Success -> conversation.value = resource.data
                is Resource.Error -> Log.e("PrivateChatVM", "Erreur observation conversation: ${resource.message}")
                is Resource.Loading -> {}
            }
        }.launchIn(viewModelScope)
    }

    private fun loadUsers() {
        firebaseAuth.currentUser?.uid?.let { uid ->
            userProfileRepository.getUserById(uid).onEach { _currentUser.value = it }.launchIn(viewModelScope)
        }
        userProfileRepository.getUserById(targetUserId).onEach { _targetUser.value = it }.launchIn(viewModelScope)
    }

    @DrawableRes
    private fun getAffinityIcon(score: Int): Int = when {
        score >= TIER_3_THRESHOLD -> R.drawable.ic_heart_fire
        score >= TIER_2_THRESHOLD -> R.drawable.ic_heart_red
        score >= TIER_1_THRESHOLD -> R.drawable.ic_heart_red
        else -> R.drawable.ic_heart_white
    }

    private fun addDateSeparators(messages: List<PrivateMessage>): List<ChatItem> {
        if (messages.isEmpty()) return emptyList()
        val fullChatList = mutableListOf<ChatItem>()
        val calendar = Calendar.getInstance()
        val sortedMessages = messages.sortedBy { it.timestamp }
        sortedMessages.forEachIndexed { index, message ->
            val messageDate = message.timestamp ?: return@forEachIndexed
            val previousMessage = sortedMessages.getOrNull(index - 1)
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

    fun formatDateLabel(date: Date): String {
        val calendar = Calendar.getInstance(); val today = calendar.time; calendar.add(Calendar.DAY_OF_YEAR, -1); val yesterday = calendar.time
        val targetCalendar = Calendar.getInstance().apply { time = date }
        return when {
            isSameDay(date, today, Calendar.getInstance()) -> "AUJOURD'HUI"
            isSameDay(date, yesterday, Calendar.getInstance()) -> "HIER"
            else -> {
                val format = if (Calendar.getInstance().get(Calendar.YEAR) == targetCalendar.get(Calendar.YEAR)) SimpleDateFormat("d MMMM", Locale.FRENCH)
                else SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
                format.format(date).uppercase(Locale.FRENCH)
            }
        }
    }

    private fun formatUserStatus(user: User?, isTyping: Boolean): String {
        if (isTyping) return "est en train d'écrire..."
        if (user?.isOnline == true) return "en ligne"
        val lastSeenDate = user?.lastSeen ?: return ""
        val diff = System.currentTimeMillis() - lastSeenDate.time
        return when (val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)) {
            in 0..0 -> "Dernière activité à l'instant"
            in 1..59 -> "Dernière activité il y a $minutes min"
            else -> when (val hours = TimeUnit.MILLISECONDS.toHours(diff)) {
                in 1..23 -> "Dernière activité il y a $hours h"
                else -> if (TimeUnit.MILLISECONDS.toDays(diff) < 2) "Dernière activité hier" else "Dernière activité le ${SimpleDateFormat("dd/MM/yy", Locale.FRENCH).format(lastSeenDate)}"
            }
        }
    }

    private var typingTimeoutJob: Job? = null
    private var isCurrentlyTyping = false
    private val textInputFlow = MutableStateFlow("")

    fun onUserTyping(text: String) { textInputFlow.value = text }
    private fun observeTypingStatus() {
        textInputFlow.debounce(TYPING_DEBOUNCE_MS).onEach {
            typingTimeoutJob?.cancel()
            val currentUserId = firebaseAuth.currentUser?.uid; val convId = conversationId.value
            if (currentUserId == null || convId == null) return@onEach
            if (it.isNotBlank() && !isCurrentlyTyping) {
                isCurrentlyTyping = true
                privateChatRepository.updateTypingStatus(convId, currentUserId, true)
            }
            if (it.isBlank() && isCurrentlyTyping) setTypingStatus(false)
            else if (it.isNotBlank()) {
                typingTimeoutJob = viewModelScope.launch { delay(TYPING_TIMEOUT_MS); setTypingStatus(false) }
            }
        }.launchIn(viewModelScope)
    }
    private fun setTypingStatus(isTyping: Boolean) {
        typingTimeoutJob?.cancel()
        val currentUserId = firebaseAuth.currentUser?.uid; val convId = conversationId.value
        if (currentUserId != null && convId != null && isCurrentlyTyping != isTyping) {
            isCurrentlyTyping = isTyping; viewModelScope.launch { privateChatRepository.updateTypingStatus(convId, currentUserId, isTyping) }
        } else if (isCurrentlyTyping != isTyping) { isCurrentlyTyping = isTyping }
    }

    fun setChatActive(isActive: Boolean) {
        _isChatVisible.value = isActive; val convId = conversationId.value; val userId = firebaseAuth.currentUser?.uid
        if (convId != null && userId != null) { viewModelScope.launch { privateChatRepository.updateUserActiveStatus(convId, userId, isActive) } }
        if (isActive && convId != null) { markConversationAsRead(convId) }
    }
    fun onReplyMessage(message: PrivateMessage) { _replyingToMessage.value = message }
    fun cancelReply() { _replyingToMessage.value = null }
    fun sendImageMessage(uri: Uri) {
        val convId = conversationId.value ?: return
        viewModelScope.launch {
            _sendState.value = Resource.Loading(); setTypingStatus(false)
            _sendState.value = privateChatRepository.sendImageMessage(convId, uri, null)
        }
    }

    // === DÉBUT DE LA CORRECTION CRITIQUE ===
    fun deleteMessage(messageId: String) {
        val convId = conversationId.value
        if (convId == null || messageId.isBlank()) return
        viewModelScope.launch {
            _deleteState.value = Resource.Loading()
            val result = privateChatRepository.deletePrivateMessage(convId, messageId)
            _deleteState.value = result

            // Mise à jour optimiste de l'UI si la suppression réussit
            if (result is Resource.Success) {
                _messages.update { currentMessages ->
                    currentMessages.filterNot { it.id == messageId }
                }
            }
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val convId = conversationId.value
        if (convId == null || messageId.isBlank() || newText.isBlank()) return
        viewModelScope.launch {
            _editState.value = Resource.Loading()
            val result = privateChatRepository.editPrivateMessage(convId, messageId, newText)
            _editState.value = result

            // Mise à jour optimiste de l'UI si la modification réussit
            if (result is Resource.Success) {
                _messages.update { currentMessages ->
                    currentMessages.map { message ->
                        if (message.id == messageId) {
                            message.copy(text = newText, isEdited = true)
                        } else {
                            message
                        }
                    }
                }
            }
        }
    }
    // === FIN DE LA CORRECTION CRITIQUE ===

    fun resetDeleteState() { _deleteState.value = null }
    fun resetEditState() { _editState.value = null }
    fun addOrUpdateReaction(messageId: String, emoji: String) {
        val convId = conversationId.value; val uid = firebaseAuth.currentUser?.uid
        if (convId == null || uid == null) return
        viewModelScope.launch { privateChatRepository.addOrUpdateReaction(convId, messageId, uid, emoji) }
    }
    private fun markConversationAsRead(conversationId: String) {
        viewModelScope.launch { privateChatRepository.markConversationAsRead(conversationId) }
    }
    override fun onCleared() { super.onCleared(); setChatActive(false); setTypingStatus(false); cancelReply() }
}