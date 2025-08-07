// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ProfileViewModel.kt
package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.Role
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.data.repository.AuthRepository
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ProfileEvent {
    data class ShowSnackbar(val message: String) : ProfileEvent()
}

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val user: User? = null,
    val currentReading: PrivateCurrentReadingUiState = PrivateCurrentReadingUiState(),
    val screenError: String? = null,
    // JUSTIFICATION DE L'AJOUT : Ce booléen expose directement à l'UI si l'utilisateur
    // est un administrateur. Le Fragment n'aura plus qu'à observer cette valeur
    // pour afficher ou cacher les options d'administration.
    val isAdmin: Boolean = false
)

data class PrivateCurrentReadingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bookReading: UserBookReading? = null,
    val bookDetails: Book? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val bookRepository: BookRepository,
    private val readingRepository: ReadingRepository,
    // JUSTIFICATION DE L'AJOUT : Injection du AuthRepository pour accéder
    // à l'état d'authentification et au rôle de l'utilisateur.
    private val authRepository: AuthRepository,
    // Note : FirebaseAuth pourrait être supprimé si toutes ses utilisations sont remplacées par AuthRepository.
    // Pour l'instant, nous le gardons pour une transition en douceur.
    internal val firebaseAuth: FirebaseAuth
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<ProfileEvent>()
    val eventFlow: SharedFlow<ProfileEvent> = _eventFlow.asSharedFlow()

    init {
        Log.d(TAG, "ViewModel initialisé. Lancement du chargement des données du profil.")
        loadProfileAndReadingData()
    }

    private fun loadProfileAndReadingData() {
        val userWithRoleFlow = authRepository.getCurrentUserWithRole()

        viewModelScope.launch {
            userWithRoleFlow.flatMapLatest { userWithRole ->
                if (userWithRole == null) {
                    flowOf(ProfileUiState(isLoading = false, screenError = "Utilisateur non connecté."))
                } else {
                    userProfileRepository.getUserById(userWithRole.uid)
                        .combine(getCurrentReadingFlow(userWithRole.uid)) { userResource, readingState ->
                            when (userResource) {
                                is Resource.Loading<*> -> ProfileUiState(isLoading = true)
                                is Resource.Error<*> -> ProfileUiState(isLoading = false, screenError = userResource.message)
                                is Resource.Success<*> -> {
                                    val fullUser = userResource.data?.apply { role = userWithRole.role }
                                    ProfileUiState(
                                        isLoading = false,
                                        user = fullUser,
                                        isAdmin = userWithRole.role == Role.ADMIN,
                                        currentReading = readingState,
                                        screenError = null
                                    )
                                }
                            }
                        }
                }
            }
                .catch { e ->
                    Log.e(TAG, "Exception dans le flow combiné de chargement du profil.", e)
                    emit(ProfileUiState(isLoading = false, screenError = "Une erreur est survenue: ${e.localizedMessage}"))
                }
                .collectLatest { newState ->
                    _uiState.value = newState
                }
        }
    }

    private fun getCurrentReadingFlow(userId: String): Flow<PrivateCurrentReadingUiState> {
        return readingRepository.getCurrentReading(userId)
            .flatMapLatest { readingResource ->
                when (readingResource) {
                    is Resource.Loading<*> -> flowOf(PrivateCurrentReadingUiState(isLoading = true))
                    is Resource.Error<*> -> flowOf(PrivateCurrentReadingUiState(isLoading = false, error = readingResource.message))
                    is Resource.Success<*> -> {
                        val reading = readingResource.data
                        if (reading != null) {
                            bookRepository.getBookById(reading.bookId).map { bookResource ->
                                PrivateCurrentReadingUiState(
                                    isLoading = bookResource is Resource.Loading<*>,
                                    error = if (bookResource is Resource.Error<*>) bookResource.message else null,
                                    bookReading = reading,
                                    bookDetails = if (bookResource is Resource.Success<*>) bookResource.data else null
                                )
                            }
                        } else {
                            flowOf(PrivateCurrentReadingUiState(isLoading = false))
                        }
                    }
                }
            }
            .catch { e ->
                Log.e(TAG, "Exception dans le flow de lecture en cours.", e)
                emit(PrivateCurrentReadingUiState(error = "Erreur de chargement de la lecture."))
            }
    }

    fun updateProfile(username: String, bio: String, city: String) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            viewModelScope.launch { _eventFlow.emit(ProfileEvent.ShowSnackbar("Utilisateur non connecté.")) }
            return
        }

        if (username.isBlank()) {
            viewModelScope.launch { _eventFlow.emit(ProfileEvent.ShowSnackbar("Le pseudo ne peut pas être vide.")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val usernameResult = userProfileRepository.updateUserProfile(userId, username)
            val bioResult = userProfileRepository.updateUserBio(userId, bio)
            val cityResult = userProfileRepository.updateUserCity(userId, city)

            val hasError = listOf(usernameResult, bioResult, cityResult).any { it is Resource.Error<*> }

            _uiState.update { it.copy(isSaving = false) }

            if (hasError) {
                Log.e(TAG, "Au moins une erreur lors de la mise à jour du profil.")
                _eventFlow.emit(ProfileEvent.ShowSnackbar("Erreur lors de la mise à jour du profil."))
            } else {
                Log.i(TAG, "Profil mis à jour avec succès.")
                _eventFlow.emit(ProfileEvent.ShowSnackbar("Profil enregistré avec succès !"))
            }
        }
    }

    fun updateCurrentReading(userBookReading: UserBookReading?) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            _uiState.update { it.copy(currentReading = it.currentReading.copy(error = "Utilisateur non connecté pour gérer la lecture.")) }
            Log.e(TAG, "updateCurrentReading: UserID is null, cannot update current reading.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(currentReading = it.currentReading.copy(isLoading = true, error = null)) }
            val result = readingRepository.updateCurrentReading(userId, userBookReading)
            when (result) {
                is Resource.Success<*> -> {
                    Log.i(TAG, "updateCurrentReading: Lecture en cours mise à jour avec succès.")
                }
                is Resource.Error<*> -> {
                    Log.e(TAG, "updateCurrentReading: Erreur lors de la mise à jour: ${result.message}")
                    _uiState.update { it.copy(currentReading = it.currentReading.copy(isLoading = false, error = result.message)) }
                }
                is Resource.Loading<*> -> { /* No-op */ }
            }
        }
    }

    fun setCurrentProfilePictureUrl(newUrl: String?) {
        Log.d(TAG, "setCurrentProfilePictureUrl: Tentative de mise à jour de profilePictureUrl dans l'UiState avec: '$newUrl'")
        val currentUser = _uiState.value.user
        if (currentUser != null) {
            _uiState.update { it.copy(user = currentUser.copy(profilePictureUrl = newUrl)) }
            Log.d(TAG, "setCurrentProfilePictureUrl: _uiState mis à jour avec la nouvelle URL.")
        } else {
            Log.w(TAG, "setCurrentProfilePictureUrl: Impossible de mettre à jour, l'utilisateur dans l'UiState est null.")
        }
    }
}