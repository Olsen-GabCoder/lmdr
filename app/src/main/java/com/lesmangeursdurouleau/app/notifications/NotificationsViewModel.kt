// PRÊT À COLLER - Fichier NotificationsViewModel.kt
package com.lesmangeursdurouleau.app.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Notification
import com.lesmangeursdurouleau.app.data.repository.NotificationRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val notifications: List<Notification> = emptyList()
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState(isLoading = true))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "NotificationsViewModel"
    }

    init {
        loadNotifications()
    }

    private fun loadNotifications() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            _uiState.value = NotificationsUiState(error = "Utilisateur non connecté.")
            Log.e(TAG, "Impossible de charger les notifications : utilisateur non connecté.")
            return
        }

        viewModelScope.launch {
            notificationRepository.getNotifications(userId)
                .catch { e ->
                    Log.e(TAG, "Erreur non gérée dans le flow de notifications", e)
                    _uiState.value = NotificationsUiState(error = "Une erreur technique est survenue.")
                }
                .collectLatest { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            _uiState.value = _uiState.value.copy(isLoading = true)
                        }
                        is Resource.Success -> {
                            _uiState.value = NotificationsUiState(notifications = resource.data ?: emptyList())
                        }
                        is Resource.Error -> {
                            _uiState.value = NotificationsUiState(error = resource.message)
                        }
                    }
                }
        }
    }

    fun onNotificationClicked(notification: Notification) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId.isNullOrBlank() || notification.isRead) {
            return // Ne rien faire si l'utilisateur n'est pas connecté ou si la notif est déjà lue
        }

        viewModelScope.launch {
            notificationRepository.markNotificationAsRead(userId, notification.id)
            // La mise à jour de l'UI se fera automatiquement grâce au listener de `getNotifications`
        }
    }

    fun markAllAsRead() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            return
        }

        // Optimisation : ne rien faire s'il n'y a aucune notification non lue
        if (_uiState.value.notifications.none { !it.isRead }) {
            return
        }

        viewModelScope.launch {
            val result = notificationRepository.markAllNotificationsAsRead(userId)
            if (result is Resource.Error) {
                Log.e(TAG, "Erreur lors du marquage de tout comme lu: ${result.message}")
                // On pourrait émettre un événement pour afficher un Toast/Snackbar d'erreur
            }
        }
    }
}