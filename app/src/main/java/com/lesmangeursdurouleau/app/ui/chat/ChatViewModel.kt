package com.lesmangeursdurouleau.app.ui.chat

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.ChatRepository
// MODIFIÉ: Import de UserProfileRepository et suppression de UserRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    // MODIFIÉ: Remplacement de UserRepository par UserProfileRepository
    private val userProfileRepository: UserProfileRepository,
    val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore // Injected FirebaseFirestore
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val HISTORY_PAGE_SIZE = 20L
        private const val TYPING_TIMEOUT_MS = 3000L // Timeout for typing status (3 seconds)
    }

    // Messages principaux (temps réel)
    private val _messages = MutableLiveData<Resource<List<Message>>>()
    val messages: LiveData<Resource<List<Message>>> = _messages

    // Messages d'historique (pagination)
    private val _oldMessagesState = MutableLiveData<Resource<List<Message>>?>()
    val oldMessagesState: MutableLiveData<Resource<List<Message>>?> = _oldMessagesState

    // Statut d'envoi de message
    private val _sendMessageStatus = MutableLiveData<Resource<Unit>?>()
    val sendMessageStatus: LiveData<Resource<Unit>?> = _sendMessageStatus

    // Statut de suppression de message
    private val _deleteMessageStatus = MutableLiveData<Resource<Unit>?>()
    val deleteMessageStatus: LiveData<Resource<Unit>?> = _deleteMessageStatus

    // NOUVEAU: Statut de l'ajout/suppression de réaction
    private val _reactionStatus = MutableLiveData<Resource<Unit>?>()
    val reactionStatus: LiveData<Resource<Unit>?> = _reactionStatus

    // Cache des détails utilisateur
    private val _userDetailsCache = MutableLiveData<Map<String, User>>()
    val userDetailsCache: LiveData<Map<String, User>> = _userDetailsCache

    // Utilisateurs en train d'écrire
    private val _typingUsers = MutableLiveData<Set<String>>(emptySet()) // Initialize with an empty set
    val typingUsers: LiveData<Set<String>> = _typingUsers

    // Variables pour la gestion de la pagination
    private var _oldestLoadedMessageTimestamp: Date? = null
    var allOldMessagesLoaded = false
        private set

    private var isLoadingMoreMessages = false

    // Typing indicator specific variables
    private var typingJob: Job? = null
    private var isCurrentlyTypingSent = false // Flag to track if 'typing:true' has been sent
    private var typingStatusListener: ListenerRegistration? = null // For observing other users

    private val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    init {
        Log.d(TAG, "ChatViewModel initialisé")
        loadGeneralChatMessages()
        observeOtherUsersTypingStatus() // Start observing other users' typing status
    }

    private fun loadGeneralChatMessages() {
        Log.d(TAG, "loadGeneralChatMessages appelé")
        chatRepository.getGeneralChatMessages()
            .onEach { resource ->
                Log.d(TAG, "Réception des messages: $resource")
                _messages.value = resource

                if (resource is Resource.Success && !resource.data.isNullOrEmpty()) {
                    updateOldestMessageTimestamp(resource.data)
                    loadUserDetailsForMessages(resource.data)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun updateOldestMessageTimestamp(messages: List<Message>) {
        val oldestMessage = messages.minByOrNull { it.timestamp ?: Date(0) }
        oldestMessage?.timestamp?.let { timestamp ->
            if (_oldestLoadedMessageTimestamp == null || timestamp.before(_oldestLoadedMessageTimestamp)) {
                _oldestLoadedMessageTimestamp = timestamp
                Log.d(TAG, "Timestamp du message le plus ancien mis à jour: $timestamp")
            }
        }
    }

    fun loadPreviousMessages() {
        if (isLoadingMoreMessages || allOldMessagesLoaded) {
            Log.d(TAG, "loadPreviousMessages: Chargement déjà en cours ou terminé")
            return
        }

        val oldestTimestamp = _oldestLoadedMessageTimestamp
        if (oldestTimestamp == null) {
            Log.w(TAG, "loadPreviousMessages: Pas de timestamp de référence")
            return
        }

        Log.d(TAG, "loadPreviousMessages: Chargement de l'historique depuis $oldestTimestamp")
        isLoadingMoreMessages = true

        chatRepository.getPreviousChatMessages(oldestTimestamp, HISTORY_PAGE_SIZE)
            .onEach { resource ->
                Log.d(TAG, "Réception de l'historique: $resource")
                _oldMessagesState.value = resource

                when (resource) {
                    is Resource.Success -> {
                        val oldMessages = resource.data
                        if (oldMessages.isNullOrEmpty()) {
                            allOldMessagesLoaded = true
                            Log.d(TAG, "Fin de l'historique atteinte")
                        } else {
                            updateOldestMessageTimestamp(oldMessages)
                            loadUserDetailsForMessages(oldMessages)
                            if (oldMessages.size < HISTORY_PAGE_SIZE) {
                                allOldMessagesLoaded = true
                                Log.d(TAG, "Fin de l'historique atteinte (moins de messages que demandé)")
                            }
                        }
                        isLoadingMoreMessages = false
                    }
                    is Resource.Error -> {
                        isLoadingMoreMessages = false
                    }
                    is Resource.Loading -> {}
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadUserDetailsForMessages(messages: List<Message>) {
        val userIds = messages.map { it.senderId }.distinct()
        val currentCache = _userDetailsCache.value ?: emptyMap()
        val newUserIds = userIds.filter { !currentCache.containsKey(it) }

        if (newUserIds.isNotEmpty()) {
            Log.d(TAG, "Chargement des détails pour ${newUserIds.size} nouveaux utilisateurs")
            viewModelScope.launch {
                try {
                    val newUserDetails = mutableMapOf<String, User>()
                    newUserIds.forEach { userId ->
                        // MODIFIÉ: Appel sur userProfileRepository
                        userProfileRepository.getUserById(userId).onEach { result ->
                            if (result is Resource.Success<User>) {
                                result.data?.let { user ->
                                    newUserDetails[userId] = user
                                }
                            } else {
                                Log.w(TAG, "Impossible de charger les détails de l'utilisateur $userId")
                            }
                        }.launchIn(viewModelScope) // Note: This launches a new coroutine for each user. Be mindful of performance on large lists.
                    }
                    _userDetailsCache.value = currentCache + newUserDetails
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur lors du chargement des détails utilisateur", e)
                }
            }
        }
    }

    fun userStartedTyping() {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "userStartedTyping: Current user ID is null, cannot update typing status.")
            return
        }

        typingJob?.cancel()

        if (!isCurrentlyTypingSent) {
            Log.d(TAG, "userStartedTyping: Signaling typing: true for user $userId")
            viewModelScope.launch {
                // MODIFIÉ: Appel sur userProfileRepository
                userProfileRepository.updateUserTypingStatus(userId, true)
                isCurrentlyTypingSent = true
            }
        }

        typingJob = viewModelScope.launch {
            delay(TYPING_TIMEOUT_MS)
            Log.d(TAG, "userStoppedTyping: Typing timeout reached for user $userId")
            userStoppedTyping()
        }
    }

    fun userStoppedTyping() {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "userStoppedTyping: Current user ID is null, cannot update typing status.")
            return
        }

        typingJob?.cancel()
        typingJob = null

        if (isCurrentlyTypingSent) {
            Log.d(TAG, "userStoppedTyping: Signaling typing: false for user $userId")
            viewModelScope.launch {
                // MODIFIÉ: Appel sur userProfileRepository
                userProfileRepository.updateUserTypingStatus(userId, false)
                isCurrentlyTypingSent = false
            }
        }
    }

    private fun observeOtherUsersTypingStatus() {
        val currentUserId = currentUserId
        if (currentUserId == null) {
            Log.w(TAG, "observeOtherUsersTypingStatus: Current user ID is null, cannot observe typing statuses.")
            return
        }

        typingStatusListener = firestore.collection("users")
            .whereEqualTo("isTypingInGeneralChat", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Listen for typing status failed.", e)
                    _typingUsers.value = emptySet()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val typingUserIds = mutableSetOf<String>()
                    for (doc in snapshots.documents) {
                        val userId = doc.id
                        if (userId != currentUserId) {
                            typingUserIds.add(userId)
                        }
                    }
                    Log.d(TAG, "Users currently typing (observed): $typingUserIds")
                    _typingUsers.value = typingUserIds
                } else {
                    Log.d(TAG, "No typing users found or snapshots is null.")
                    _typingUsers.value = emptySet()
                }
            }
    }

    fun sendMessage(text: String) {
        Log.d(TAG, "sendMessage appelé avec: \"$text\"")
        val senderUid = firebaseAuth.currentUser?.uid
        val senderUsername = firebaseAuth.currentUser?.displayName

        if (senderUid == null) {
            Log.e(TAG, "sendMessage: Current user is not authenticated.")
            _sendMessageStatus.value = Resource.Error("User not authenticated.")
            return
        }

        val message = Message(
            text = text,
            senderId = senderUid,
            senderUsername = senderUsername ?: "Anonymous",
            timestamp = Date(),
            messageId = ""
        )

        viewModelScope.launch {
            _sendMessageStatus.value = Resource.Loading()
            val result = chatRepository.sendGeneralChatMessage(message)
            _sendMessageStatus.value = result
            Log.d(TAG, "Résultat d'envoi: $result")
            userStoppedTyping()
        }
    }

    fun deleteMessage(messageId: String) {
        Log.d(TAG, "deleteMessage appelé pour: $messageId")
        viewModelScope.launch {
            _deleteMessageStatus.value = Resource.Loading()
            val result = chatRepository.deleteChatMessage(messageId)
            _deleteMessageStatus.value = result
            Log.d(TAG, "Résultat de suppression: $result")
        }
    }

    fun addReactionToMessage(messageId: String, reactionEmoji: String) {
        val userId = currentUserId
        if (userId == null) {
            Log.e(TAG, "addReactionToMessage: Current user ID is null, cannot add reaction.")
            _reactionStatus.value = Resource.Error("Utilisateur non authentifié.")
            return
        }

        Log.d(TAG, "addReactionToMessage appelé pour message $messageId avec réaction '$reactionEmoji' par user $userId")
        viewModelScope.launch {
            _reactionStatus.value = Resource.Loading()
            val result = chatRepository.toggleMessageReaction(messageId, reactionEmoji, userId)
            _reactionStatus.value = result
            Log.d(TAG, "Résultat de la réaction: $result")
        }
    }

    fun clearSendMessageStatus() {
        _sendMessageStatus.value = null
    }

    fun clearDeleteMessageStatus() {
        _deleteMessageStatus.value = null
    }

    fun clearReactionStatus() {
        _reactionStatus.value = null
    }

    fun resetPaginationState() {
        _oldestLoadedMessageTimestamp = null
        allOldMessagesLoaded = false
        isLoadingMoreMessages = false
        _oldMessagesState.value = null
        Log.d(TAG, "État de pagination réinitialisé")
    }

    override fun onCleared() {
        super.onCleared()
        typingJob?.cancel()
        userStoppedTyping()
        typingStatusListener?.remove()
        Log.d(TAG, "ChatViewModel cleared. Listeners removed.")
    }
}