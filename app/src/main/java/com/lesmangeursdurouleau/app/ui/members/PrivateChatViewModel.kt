// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PrivateChatViewModel.kt
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
import com.lesmangeursdurouleau.app.data.repository.PrivateChatRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TYPING_DEBOUNCE_MS = 500L
        private const val TYPING_TIMEOUT_MS = 3000L
        private const val TIER_1_THRESHOLD = 21
        private const val TIER_2_THRESHOLD = 51
        private const val TIER_3_THRESHOLD = 101
    }

    private val targetUserId: String = savedStateHandle.get<String>("targetUserId")!!
    val conversationId = MutableStateFlow<String?>(null)

    private val _isLoadingMessages = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoadingMessages

    private val _messages = MutableStateFlow<List<PrivateMessage>>(emptyList())
    val chatItems: StateFlow<List<ChatItem>> = _messages
        .map { addDateSeparators(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _isTargetUserTyping = conversation
        .map { it?.typingStatus?.get(targetUserId) ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ToolbarState())

    init {
        initializeAndLoadData()
    }

    private fun initializeAndLoadData() {
        viewModelScope.launch {
            val currentUserId = firebaseAuth.currentUser?.uid ?: return@launch
            _isLoadingMessages.value = true
            val resource = privateChatRepository.createOrGetConversation(currentUserId, targetUserId)
            if (resource is Resource.Success && resource.data != null) {
                val convId = resource.data
                conversationId.value = convId
                startListeningForMessages(convId)
                observeConversation(convId)
                loadUsers(currentUserId)
                // === DÉBUT DE LA CORRECTION DU CRASH ===
                // L'observation du typing status ne doit commencer qu'APRÈS
                // que l'ID de la conversation soit connu et valide.
                observeTypingStatus()
                // === FIN DE LA CORRECTION DU CRASH ===
            } else {
                _isLoadingMessages.value = false
                val errorMsg = (resource as? Resource.Error)?.message ?: "Impossible de démarrer la conversation."
                Log.e("PrivateChatVM", errorMsg)
            }
        }
    }

    private fun startListeningForMessages(conversationId: String) {
        privateChatRepository.getConversationMessages(conversationId)
            .onEach { resource ->
                _isLoadingMessages.value = resource is Resource.Loading

                if (resource is Resource.Success) {
                    val messages = resource.data ?: emptyList()
                    _messages.value = messages

                    val hasUnreadFromOther = messages.any { it.senderId == targetUserId && it.status == MessageStatus.SENT.name }
                    if (_isChatVisible.value && hasUnreadFromOther) {
                        markConversationAsRead()
                    }
                } else if (resource is Resource.Error) {
                    Log.e("PrivateChatVM", "Erreur du listener de messages: ${resource.message}")
                }
            }.launchIn(viewModelScope)
    }

    private fun observeConversation(conversationId: String) {
        privateChatRepository.getConversation(conversationId)
            .onEach { resource ->
                if (resource is Resource.Success) {
                    val newConversation = resource.data
                    if (conversation.value != null && newConversation?.lastTierUpgradeTimestamp != conversation.value?.lastTierUpgradeTimestamp) {
                        triggerTierUpgradeEffect("Nouveau palier d'affinité atteint !")
                    }
                    conversation.value = newConversation
                } else if (resource is Resource.Error) {
                    Log.e("PrivateChatVM", "Erreur observation conversation: ${resource.message}")
                }
            }.launchIn(viewModelScope)
    }

    fun sendPrivateMessage(text: String) {
        val convId = conversationId.value ?: return
        val senderId = firebaseAuth.currentUser?.uid ?: return
        if (text.isBlank()) return

        val replyInfo = _replyingToMessage.value?.let { originalMessage ->
            val originalSenderName = if (originalMessage.senderId == senderId) "Vous" else (_targetUser.value as? Resource.Success)?.data?.username ?: ""
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

    fun sendImageMessage(uri: Uri) {
        val convId = conversationId.value
        if (convId == null) {
            viewModelScope.launch { _sendState.value = Resource.Error("ID de conversation non disponible") }
            return
        }
        viewModelScope.launch {
            _sendState.value = Resource.Loading()
            setTypingStatus(false)
            try {
                val imageData = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (imageData == null) {
                    _sendState.value = Resource.Error("Impossible de lire les données de l'image.")
                    return@launch
                }
                _sendState.value = privateChatRepository.sendImageMessage(convId, imageData, null)
            } catch (e: Exception) {
                Log.e("PrivateChatVM", "Erreur lors de la lecture de l'image URI", e)
                _sendState.value = Resource.Error("Erreur lors de la préparation de l'image: ${e.message}")
            }
        }
    }

    fun deleteMessage(messageId: String) {
        val convId = conversationId.value ?: return
        viewModelScope.launch {
            _deleteState.value = Resource.Loading()
            _deleteState.value = privateChatRepository.deletePrivateMessage(convId, messageId)
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val convId = conversationId.value ?: return
        viewModelScope.launch {
            _editState.value = Resource.Loading()
            _editState.value = privateChatRepository.editPrivateMessage(convId, messageId, newText)
        }
    }

    fun addOrUpdateReaction(messageId: String, emoji: String) {
        val convId = conversationId.value ?: return
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            privateChatRepository.addOrUpdateReaction(convId, messageId, uid, emoji)
        }
    }

    private fun loadUsers(currentUserId: String) {
        userProfileRepository.getUserById(currentUserId)
            .onEach { _currentUser.value = it }
            .launchIn(viewModelScope)

        userProfileRepository.getUserById(targetUserId)
            .onEach { _targetUser.value = it }
            .launchIn(viewModelScope)
    }

    fun triggerTierUpgradeEffect(message: String) {
        viewModelScope.launch {
            _events.emit(ChatEvent.TierUpgrade(message))
        }
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

            val isFirstMessage = index == 0
            val isNewDay = previousMessage?.timestamp != null && !isSameDay(previousMessage.timestamp, messageDate, calendar)

            if (isFirstMessage || isNewDay) {
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
        val calendar = Calendar.getInstance()
        val today = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = calendar.time

        val targetCalendar = Calendar.getInstance().apply { time = date }

        return when {
            isSameDay(date, today, Calendar.getInstance()) -> "AUJOURD'HUI"
            isSameDay(date, yesterday, Calendar.getInstance()) -> "HIER"
            else -> {
                val formatPattern = if (Calendar.getInstance().get(Calendar.YEAR) == targetCalendar.get(Calendar.YEAR)) {
                    "d MMMM"
                } else {
                    "d MMMM yyyy"
                }
                val format = SimpleDateFormat(formatPattern, Locale.FRENCH)
                format.format(date).uppercase(Locale.FRENCH)
            }
        }
    }

    private fun formatUserStatus(user: User?, isTyping: Boolean): String {
        if (isTyping) return "en train d'écrire..."
        if (user?.isOnline == true) return "en ligne"

        val lastSeenDate = user?.lastSeen ?: return ""
        val diff = System.currentTimeMillis() - lastSeenDate.time

        return when (val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)) {
            in 0..0 -> "Dernière activité à l'instant"
            in 1..59 -> "Dernière activité il y a $minutes min"
            else -> when (val hours = TimeUnit.MILLISECONDS.toHours(diff)) {
                in 1..23 -> "Dernière activité il y a $hours h"
                else -> if (TimeUnit.MILLISECONDS.toDays(diff) < 2) {
                    "Dernière activité hier"
                } else {
                    "Dernière activité le ${SimpleDateFormat("dd/MM/yy", Locale.FRENCH).format(lastSeenDate)}"
                }
            }
        }
    }

    private var typingTimeoutJob: Job? = null
    private var isCurrentlyTyping = false
    private val textInputFlow = MutableStateFlow("")

    fun onUserTyping(text: String) {
        textInputFlow.value = text
    }

    private fun observeTypingStatus() {
        textInputFlow
            .debounce(TYPING_DEBOUNCE_MS)
            .onEach { text ->
                typingTimeoutJob?.cancel()
                val isNotEmpty = text.isNotBlank()

                if (isNotEmpty && !isCurrentlyTyping) {
                    setTypingStatus(true)
                }

                if (!isNotEmpty && isCurrentlyTyping) {
                    setTypingStatus(false)
                } else if (isNotEmpty) {
                    // Si l'utilisateur tape toujours, on relance le timeout
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

        if (isCurrentlyTyping != isTyping) {
            isCurrentlyTyping = isTyping
            if (currentUserId != null && convId != null) {
                viewModelScope.launch {
                    privateChatRepository.updateTypingStatus(convId, currentUserId, isTyping)
                }
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
        if (isActive) {
            markConversationAsRead()
        }
    }

    fun onReplyMessage(message: PrivateMessage) { _replyingToMessage.value = message }
    fun cancelReply() { _replyingToMessage.value = null }
    fun resetDeleteState() { _deleteState.value = null }
    fun resetEditState() { _editState.value = null }

    private fun markConversationAsRead() {
        conversationId.value?.let { convId ->
            viewModelScope.launch {
                privateChatRepository.markConversationAsRead(convId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        setChatActive(false)
        setTypingStatus(false)
        cancelReply()
    }
}