package com.lesmangeursdurouleau.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val authRepository: AuthRepository,
    // NOUVELLE DÉPENDANCE : Nécessaire pour obtenir l'ID de l'utilisateur actuel.
    private val firebaseAuth: FirebaseAuth
) : AndroidViewModel(application) {

    private val _registrationResult = MutableLiveData<AuthResultWrapper?>()
    val registrationResult: LiveData<AuthResultWrapper?> = _registrationResult

    private val _loginResult = MutableLiveData<AuthResultWrapper?>()
    val loginResult: LiveData<AuthResultWrapper?> = _loginResult

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _justRegistered = MutableLiveData<Boolean>(false)
    val justRegistered: LiveData<Boolean> = _justRegistered

    private val _passwordResetResult = MutableLiveData<AuthResultWrapper?>()
    val passwordResetResult: LiveData<AuthResultWrapper?> = _passwordResetResult

    init {
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

    // === DÉBUT DE LA MODIFICATION ===
    // JUSTIFICATION : La méthode est maintenant asynchrone (suspend fun) et passe l'ID de l'utilisateur
    // actuel au repository pour assurer la mise à jour du statut de présence avant la déconnexion.
    fun logoutUser() {
        viewModelScope.launch {
            val userId = firebaseAuth.currentUser?.uid
            authRepository.logoutUser(userId)
        }
    }
    // === FIN DE LA MODIFICATION ===

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