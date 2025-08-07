// PRÊT À COLLER - Créez un nouveau fichier AuthRepositoryImpl.kt dans le package data/repository
package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.model.Role
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.ui.auth.AuthResultWrapper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    private companion object {
        const val TAG = "AuthRepositoryImpl"
    }

    override fun getCurrentUserWithRole(): Flow<User?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                // Forcer le rafraîchissement du jeton pour obtenir les derniers custom claims
                firebaseUser.getIdToken(true).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val isAdmin = task.result?.claims?.get("admin") as? Boolean ?: false
                        val userRole = if (isAdmin) Role.ADMIN else Role.USER

                        Log.d(TAG, "Rôle de l'utilisateur ${firebaseUser.uid} : $userRole")

                        // Construire notre objet User avec le rôle
                        val user = User(
                            uid = firebaseUser.uid,
                            email = firebaseUser.email ?: "",
                            username = firebaseUser.displayName ?: "",
                            role = userRole
                        )
                        trySend(user)
                    } else {
                        Log.e(TAG, "Échec de la récupération du jeton avec les claims.", task.exception)
                        // En cas d'échec, envoyer l'utilisateur avec le rôle par défaut
                        trySend(User(uid = firebaseUser.uid, email = firebaseUser.email ?: "", username = firebaseUser.displayName ?: "", role = Role.USER))
                    }
                }
            }
        }
        firebaseAuth.addAuthStateListener(authStateListener)
        awaitClose { firebaseAuth.removeAuthStateListener(authStateListener) }
    }


    override suspend fun loginUser(email: String, password: String): AuthResultWrapper {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user!!
            firebaseUser.reload().await()
            val refreshedUser = firebaseAuth.currentUser!!
            if (refreshedUser.isEmailVerified) {
                AuthResultWrapper.Success(refreshedUser)
            } else {
                firebaseAuth.signOut()
                AuthResultWrapper.EmailNotVerified
            }
        } catch (e: Exception) {
            val errorCode = (e as? FirebaseAuthException)?.errorCode
            AuthResultWrapper.Error(e, errorCode)
        }
    }

    override suspend fun signInWithGoogleToken(idToken: String): AuthResultWrapper {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user!!
            val isNewUser = authResult.additionalUserInfo?.isNewUser ?: false

            if (isNewUser) {
                val userDocument = hashMapOf(
                    "uid" to firebaseUser.uid,
                    "username" to (firebaseUser.displayName ?: firebaseUser.email ?: "Utilisateur Google"),
                    "email" to firebaseUser.email,
                    "profilePictureUrl" to (firebaseUser.photoUrl?.toString()),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "isEmailVerified" to true
                )
                firestore.collection(FirebaseConstants.COLLECTION_USERS).document(firebaseUser.uid).set(userDocument).await()
            }
            AuthResultWrapper.Success(firebaseUser)
        } catch (e: Exception) {
            if (e is FirebaseAuthUserCollisionException) {
                AuthResultWrapper.AccountExistsWithDifferentCredential(e.email!!, e.updatedCredential!!)
            } else {
                val errorCode = (e as? FirebaseAuthException)?.errorCode
                AuthResultWrapper.Error(e, errorCode)
            }
        }
    }

    override suspend fun registerUser(email: String, password: String, username: String): AuthResultWrapper {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user!!
            firebaseUser.sendEmailVerification().await()

            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(username).build()
            firebaseUser.updateProfile(profileUpdates).await()

            val userDocument = hashMapOf(
                "uid" to firebaseUser.uid,
                "username" to username,
                "email" to email,
                "createdAt" to FieldValue.serverTimestamp(),
                "isEmailVerified" to false
            )
            firestore.collection(FirebaseConstants.COLLECTION_USERS).document(firebaseUser.uid).set(userDocument).await()

            firebaseAuth.signOut()
            AuthResultWrapper.Success(firebaseUser)
        } catch (e: Exception) {
            val errorCode = (e as? FirebaseAuthException)?.errorCode
            AuthResultWrapper.Error(e, errorCode)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResultWrapper {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            AuthResultWrapper.Success()
        } catch (e: Exception) {
            val errorCode = (e as? FirebaseAuthException)?.errorCode
            AuthResultWrapper.Error(e, errorCode)
        }
    }

    override fun logoutUser() {
        firebaseAuth.signOut()
    }

    override suspend fun linkGoogleAccount(pendingCredential: AuthCredential, email: String, password: String): AuthResultWrapper {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user!!
            val linkResult = user.linkWithCredential(pendingCredential).await()
            AuthResultWrapper.Success(linkResult.user)
        } catch (e: Exception) {
            val errorCode = (e as? FirebaseAuthException)?.errorCode
            AuthResultWrapper.Error(e, errorCode)
        }
    }
}