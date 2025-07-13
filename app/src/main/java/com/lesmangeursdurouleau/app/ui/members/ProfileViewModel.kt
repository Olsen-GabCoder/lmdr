// PRÊT À COLLER - Fichier ProfileViewModel.kt mis à jour
package com.lesmangeursdurouleau.app.ui.members

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.ReadingRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// JUSTIFICATION : La structure de l'événement reste la même, c'est une architecture saine.
sealed class ProfileEvent {
    data class ShowSnackbar(val message: String) : ProfileEvent()
}

// JUSTIFICATION : La classe d'état unique (UiState) reste le pilier de notre architecture MVI.
data class ProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val user: User? = null,
    val currentReading: PrivateCurrentReadingUiState = PrivateCurrentReadingUiState(),
    val screenError: String? = null
)

// JUSTIFICATION : L'état imbriqué pour la lecture en cours est conservé pour une bonne organisation.
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
    internal val firebaseAuth: FirebaseAuth
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    // JUSTIFICATION : La source de vérité unique est conservée.
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // JUSTIFICATION : Le SharedFlow pour les événements ponctuels est conservé.
    private val _eventFlow = MutableSharedFlow<ProfileEvent>()
    val eventFlow: SharedFlow<ProfileEvent> = _eventFlow.asSharedFlow()

    init {
        Log.d(TAG, "ViewModel initialisé. Lancement du chargement des données du profil.")
        loadProfileAndReadingData()
    }

    private fun loadProfileAndReadingData() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, screenError = "Utilisateur non connecté.") }
            Log.e(TAG, "loadProfileAndReadingData: Impossible de charger les données, UID utilisateur est null.")
            return
        }

        viewModelScope.launch {
            userProfileRepository.getUserById(userId)
                .combine(getCurrentReadingFlow(userId)) { userResource, readingState ->
                    // JUSTIFICATION : CORRECTION SYNTAXIQUE.
                    // Remplacement de `is Resource.Loading` par `is Resource.Loading<*>` (et de même pour Error et Success)
                    // pour se conformer aux exigences du compilateur Kotlin pour les classes génériques.
                    when (userResource) {
                        is Resource.Loading<*> -> _uiState.value.copy(isLoading = true, screenError = null)
                        is Resource.Error<*> -> _uiState.value.copy(isLoading = false, screenError = userResource.message)
                        is Resource.Success<*> -> _uiState.value.copy(
                            isLoading = false,
                            user = userResource.data,
                            currentReading = readingState,
                            screenError = null
                        )
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Exception dans le flow combiné de chargement du profil.", e)
                    _uiState.update { it.copy(isLoading = false, screenError = "Une erreur est survenue: ${e.localizedMessage}") }
                }
                .collectLatest { newState ->
                    _uiState.value = newState
                }
        }
    }

    private fun getCurrentReadingFlow(userId: String): Flow<PrivateCurrentReadingUiState> {
        return readingRepository.getCurrentReading(userId)
            .flatMapLatest { readingResource ->
                // JUSTIFICATION : CORRECTION SYNTAXIQUE
                when (readingResource) {
                    is Resource.Loading<*> -> flowOf(PrivateCurrentReadingUiState(isLoading = true))
                    is Resource.Error<*> -> flowOf(PrivateCurrentReadingUiState(isLoading = false, error = readingResource.message))
                    is Resource.Success<*> -> {
                        val reading = readingResource.data
                        if (reading != null) {
                            bookRepository.getBookById(reading.bookId).map { bookResource ->
                                // JUSTIFICATION : CORRECTION SYNTAXIQUE
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

            // JUSTIFICATION : CORRECTION SYNTAXIQUE
            val hasError = listOf(usernameResult, bioResult, cityResult).any { it is Resource.Error<*> }

            _uiState.update { it.copy(isSaving = false) }

            if (hasError) {
                Log.e(TAG, "Au moins une erreur lors de la mise à jour du profil.")
                _eventFlow.emit(ProfileEvent.ShowSnackbar("Erreur lors de la mise à jour du profil."))
                loadProfileAndReadingData()
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
            // JUSTIFICATION : CORRECTION SYNTAXIQUE
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