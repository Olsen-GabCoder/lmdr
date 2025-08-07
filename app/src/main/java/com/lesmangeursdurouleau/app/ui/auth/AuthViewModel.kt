// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier AuthViewModel.kt
package com.lesmangeursdurouleau.app.ui.auth

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.AuthRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

// JUSTIFICATION : Cette classe de données reste ici car elle est spécifique à la communication
// entre le ViewModel et les Fragments d'authentification.
sealed class AuthResultWrapper {
    data class Success(val user: FirebaseUser? = null) : AuthResultWrapper()
    data class Error(val exception: Exception, val errorCode: String? = null) : AuthResultWrapper()
    object EmailNotVerified : AuthResultWrapper()
    object Loading : AuthResultWrapper()
    data class AccountExistsWithDifferentCredential(val email: String, val pendingCredential: AuthCredential) : AuthResultWrapper()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    // JUSTIFICATION DE LA MODIFICATION : Le ViewModel n'injecte plus FirebaseAuth ou FirebaseFirestore.
    // Il dépend désormais uniquement de nos abstractions (Repositories), ce qui respecte la Clean Architecture.
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) : AndroidViewModel(application) {

    private val _registrationResult = MutableLiveData<AuthResultWrapper?>()
    val registrationResult: LiveData<AuthResultWrapper?> = _registrationResult

    private val _loginResult = MutableLiveData<AuthResultWrapper?>()
    val loginResult: LiveData<AuthResultWrapper?> = _loginResult

    // JUSTIFICATION DE LA MODIFICATION : _currentUser est maintenant un StateFlow de notre propre modèle User (avec son rôle).
    // Il est alimenté par le Flow réactif du AuthRepository, ce qui est plus moderne et robuste que l'ancien LiveData.
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _justRegistered = MutableLiveData<Boolean>(false)
    val justRegistered: LiveData<Boolean> = _justRegistered

    private val _passwordResetResult = MutableLiveData<AuthResultWrapper?>()
    val passwordResetResult: LiveData<AuthResultWrapper?> = _passwordResetResult

    private val _profileUpdateResult = MutableLiveData<Resource<Unit>?>()
    val profileUpdateResult: LiveData<Resource<Unit>?> = _profileUpdateResult

    private val _profilePictureUpdateResult = MutableStateFlow<Resource<String>?>(null)
    val profilePictureUpdateResult: StateFlow<Resource<String>?> = _profilePictureUpdateResult.asStateFlow()

    private val _coverPictureUpdateResult = MutableStateFlow<Resource<String>?>(null)
    val coverPictureUpdateResult: StateFlow<Resource<String>?> = _coverPictureUpdateResult.asStateFlow()

    init {
        // Observer l'état de l'utilisateur depuis le repository
        viewModelScope.launch {
            authRepository.getCurrentUserWithRole().collectLatest { userWithRole ->
                _currentUser.value = userWithRole
            }
        }
    }

    fun registerUser(email: String, password: String, username: String) {
        viewModelScope.launch {
            _registrationResult.value = AuthResultWrapper.Loading
            val result = authRepository.registerUser(email, password, username)
            if (result is AuthResultWrapper.Success) {
                _justRegistered.value = true
            }
            _registrationResult.value = result
        }
    }

    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            _loginResult.value = AuthResultWrapper.Loading
            _loginResult.value = authRepository.loginUser(email, password)
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        viewModelScope.launch {
            _loginResult.value = AuthResultWrapper.Loading
            _loginResult.value = authRepository.signInWithGoogleToken(idToken)
        }
    }

    fun linkGoogleAccountToExistingEmailUser(pendingCredential: AuthCredential, email: String, password: String) {
        viewModelScope.launch {
            _loginResult.value = AuthResultWrapper.Loading
            _loginResult.value = authRepository.linkGoogleAccount(pendingCredential, email, password)
        }
    }

    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _passwordResetResult.value = AuthResultWrapper.Loading
            _passwordResetResult.value = authRepository.sendPasswordResetEmail(email)
        }
    }

    fun logoutUser() {
        authRepository.logoutUser()
    }

    // Les fonctions de mise à jour du profil restent, car elles concernent le profil utilisateur et non l'auth pure.
    fun updateUserProfile(userId: String, username: String) {
        viewModelScope.launch {
            _profileUpdateResult.value = Resource.Loading()
            val result = userProfileRepository.updateUserProfile(userId, username)
            _profileUpdateResult.value = result
        }
    }

    fun updateProfilePicture(uri: Uri) {
        val userId = _currentUser.value?.uid
        if (userId.isNullOrBlank()) {
            _profilePictureUpdateResult.value = Resource.Error("Utilisateur non connecté.")
            return
        }
        viewModelScope.launch {
            _profilePictureUpdateResult.value = Resource.Loading()
            try {
                val imageData = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (imageData != null) {
                    val result = userProfileRepository.updateUserProfilePicture(userId, imageData)
                    _profilePictureUpdateResult.value = result
                } else {
                    _profilePictureUpdateResult.value = Resource.Error("Impossible de lire les données de l'image.")
                }
            } catch (e: IOException) {
                _profilePictureUpdateResult.value = Resource.Error("Erreur lors de la lecture du fichier image: ${e.localizedMessage}")
            }
        }
    }

    fun updateCoverPicture(uri: Uri) {
        val userId = _currentUser.value?.uid
        if (userId.isNullOrBlank()) {
            _coverPictureUpdateResult.value = Resource.Error("Utilisateur non connecté.")
            return
        }

        viewModelScope.launch {
            _coverPictureUpdateResult.value = Resource.Loading()
            try {
                val imageData = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (imageData != null) {
                    val result = userProfileRepository.updateUserCoverPicture(userId, imageData)
                    _coverPictureUpdateResult.value = result
                } else {
                    _coverPictureUpdateResult.value = Resource.Error("Impossible de lire les données de l'image.")
                }
            } catch (e: IOException) {
                _coverPictureUpdateResult.value = Resource.Error("Erreur lors de la lecture du fichier image: ${e.localizedMessage}")
            }
        }
    }

    fun consumeLoginResult() {
        _loginResult.value = null
    }

    fun consumeRegistrationResult() {
        _registrationResult.value = null
    }

    fun consumePasswordResetResult() {
        _passwordResetResult.value = null
    }

    fun consumeJustRegisteredEvent() {
        _justRegistered.value = false
    }
}