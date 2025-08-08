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
import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.data.repository.AuthRepository
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.domain.usecase.library.GetCurrentlyReadingEntryUseCase
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
    val isAdmin: Boolean = false
)

// MODIFIÉ : Ajout de libraryEntry pour la nouvelle logique, tout en conservant bookReading pour la compatibilité.
data class PrivateCurrentReadingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bookReading: UserBookReading? = null, // Conservé pour le Fragment
    val libraryEntry: UserLibraryEntry? = null, // La nouvelle source de vérité
    val bookDetails: Book? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val bookRepository: BookRepository,
    private val authRepository: AuthRepository,
    private val getCurrentlyReadingEntryUseCase: GetCurrentlyReadingEntryUseCase, // NOUVELLE DÉPENDANCE
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

                                // Reconstruire le modèle "legacy" UserBookReading pour la compatibilité avec le Fragment
                                val legacyBookReading = bookDetails?.let {
                                    UserBookReading(
                                        bookId = entry.bookId,
                                        title = it.title,
                                        author = it.author,
                                        coverImageUrl = it.coverImageUrl,
                                        currentPage = entry.currentPage,
                                        totalPages = entry.totalPages,
                                        favoriteQuote = entry.favoriteQuote,
                                        personalReflection = entry.personalReflection
                                    )
                                }

                                PrivateCurrentReadingUiState(
                                    isLoading = bookResource is Resource.Loading<*>,
                                    error = if (bookResource is Resource.Error<*>) bookResource.message else null,
                                    bookReading = legacyBookReading, // On peuple l'ancien champ
                                    libraryEntry = entry,           // On peuple le nouveau champ
                                    bookDetails = bookDetails
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