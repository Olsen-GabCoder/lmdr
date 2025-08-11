package com.lesmangeursdurouleau.app.ui.members

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.Role
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserBookReading
import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.data.repository.AuthRepository
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.domain.usecase.library.GetCurrentlyReadingEntryUseCase
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

sealed class ProfileEvent {
    data class ShowSnackbar(val message: String) : ProfileEvent()
    object ImageUpdateFinished : ProfileEvent()
    // NOUVEAU : Événement pour déclencher la navigation après la déconnexion
    object NavigateToAuthScreen : ProfileEvent()
}

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val user: User? = null,
    val currentReading: PrivateCurrentReadingUiState = PrivateCurrentReadingUiState(),
    val screenError: String? = null,
    val isAdmin: Boolean = false,
    val isUploadingProfilePicture: Boolean = false,
    val isUploadingCoverPicture: Boolean = false
)

data class PrivateCurrentReadingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bookReading: UserBookReading? = null,
    val libraryEntry: UserLibraryEntry? = null,
    val bookDetails: Book? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val bookRepository: BookRepository,
    private val authRepository: AuthRepository,
    private val getCurrentlyReadingEntryUseCase: GetCurrentlyReadingEntryUseCase,
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
        loadProfileAndReadingData()
    }

    // === DÉBUT DE LA MODIFICATION ===
    /**
     * NOUVEAU : Orchestre la déconnexion de l'utilisateur.
     * 1. Récupère l'ID de l'utilisateur actuel.
     * 2. Appelle le AuthRepository pour mettre à jour le statut de présence ET se déconnecter.
     * 3. Émet un événement pour que le Fragment déclenche la navigation.
     */
    fun logout() {
        viewModelScope.launch {
            val userId = firebaseAuth.currentUser?.uid
            if (userId != null) {
                authRepository.logoutUser(userId)
            } else {
                // Si pas d'ID, on se déconnecte quand même localement.
                authRepository.logoutUser(null)
            }
            // Émettre l'événement de navigation après la fin des opérations.
            _eventFlow.emit(ProfileEvent.NavigateToAuthScreen)
        }
    }
    // === FIN DE LA MODIFICATION ===

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
        return getCurrentlyReadingEntryUseCase(userId)
            .flatMapLatest { entryResource ->
                when (entryResource) {
                    is Resource.Loading<*> -> flowOf(PrivateCurrentReadingUiState(isLoading = true))
                    is Resource.Error<*> -> flowOf(PrivateCurrentReadingUiState(isLoading = false, error = entryResource.message))
                    is Resource.Success<*> -> {
                        val entry = entryResource.data
                        if (entry != null) {
                            bookRepository.getBookById(entry.bookId).map { bookResource ->
                                val bookDetails = (bookResource as? Resource.Success)?.data
                                val legacyBookReading = bookDetails?.let {
                                    UserBookReading(
                                        bookId = entry.bookId, title = it.title, author = it.author,
                                        coverImageUrl = it.coverImageUrl, currentPage = entry.currentPage,
                                        totalPages = entry.totalPages, favoriteQuote = entry.favoriteQuote,
                                        personalReflection = entry.personalReflection
                                    )
                                }
                                PrivateCurrentReadingUiState(
                                    isLoading = bookResource is Resource.Loading<*>,
                                    error = if (bookResource is Resource.Error<*>) bookResource.message else null,
                                    bookReading = legacyBookReading, libraryEntry = entry, bookDetails = bookDetails
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
        val userId = firebaseAuth.currentUser?.uid ?: return
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
                _eventFlow.emit(ProfileEvent.ShowSnackbar("Erreur lors de la mise à jour du profil."))
            } else {
                _eventFlow.emit(ProfileEvent.ShowSnackbar("Profil enregistré avec succès !"))
            }
        }
    }

    fun updateProfilePicture(imageStream: InputStream?) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingProfilePicture = true) }
            val imageData = imageStream?.use { it.readBytes() }
            if (imageData != null) {
                val result = userProfileRepository.updateUserProfilePicture(userId, imageData)
                if (result is Resource.Error) {
                    _eventFlow.emit(ProfileEvent.ShowSnackbar(result.message ?: "Erreur inconnue"))
                }
            } else {
                _eventFlow.emit(ProfileEvent.ShowSnackbar("Erreur de lecture de l'image"))
            }
            _uiState.update { it.copy(isUploadingProfilePicture = false) }
            _eventFlow.emit(ProfileEvent.ImageUpdateFinished)
        }
    }

    fun updateCoverPicture(imageStream: InputStream?) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingCoverPicture = true) }
            val imageData = imageStream?.use { it.readBytes() }
            if (imageData != null) {
                val result = userProfileRepository.updateUserCoverPicture(userId, imageData)
                if (result is Resource.Error) {
                    _eventFlow.emit(ProfileEvent.ShowSnackbar(result.message ?: "Erreur inconnue"))
                }
            } else {
                _eventFlow.emit(ProfileEvent.ShowSnackbar("Erreur de lecture de l'image"))
            }
            _uiState.update { it.copy(isUploadingCoverPicture = false) }
            _eventFlow.emit(ProfileEvent.ImageUpdateFinished)
        }
    }

    fun setCurrentProfilePictureUrl(newUrl: String?) {
        val currentUser = _uiState.value.user
        if (currentUser != null) {
            _uiState.update { it.copy(user = currentUser.copy(profilePictureUrl = newUrl)) }
        }
    }
}