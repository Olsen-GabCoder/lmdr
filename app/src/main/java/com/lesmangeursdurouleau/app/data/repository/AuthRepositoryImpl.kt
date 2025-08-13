// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier AuthRepositoryImpl.kt
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
import com.google.firebase.messaging.FirebaseMessaging // NOUVEL IMPORT
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
    private val firestore: FirebaseFirestore,
    private val userProfileRepository: UserProfileRepository,
    private val firebaseMessaging: FirebaseMessaging // NOUVELLE DÉPENDANCE
) : AuthRepository {

    private companion object {
        const val TAG = "AuthRepositoryImpl"
    }

    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    override fun getCurrentUserWithRole(): Flow<User?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                firebaseUser.getIdToken(true).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val isAdmin = task.result?.claims?.get("admin") as? Boolean ?: false
                        val userRole = if (isAdmin) Role.ADMIN else Role.USER
                        Log.d(TAG, "Rôle de l'utilisateur ${firebaseUser.uid} : $userRole")
                        val user = User(
                            uid = firebaseUser.uid,
                            email = firebaseUser.email ?: "",
                            username = firebaseUser.displayName ?: "",
                            role = userRole
                        )
                        trySend(user)
                    } else {
                        Log.e(TAG, "Échec de la récupération du jeton avec les claims.", task.exception)
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
                // === DÉBUT DE L'AJOUT CRITIQUE ===
                updateFcmToken(refreshedUser.uid)
                // === FIN DE L'AJOUT CRITIQUE ===
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
                usersCollection.document(firebaseUser.uid).set(userDocument).await()
            }
            // === DÉBUT DE L'AJOUT CRITIQUE ===
            updateFcmToken(firebaseUser.uid)
            // === FIN DE L'AJOUT CRITIQUE ===
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
            usersCollection.document(firebaseUser.uid).set(userDocument).await()

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

    override suspend fun logoutUser(userId: String?) {
        if (userId != null) {
            try {
                userProfileRepository.updateUserPresence(userId, isOnline = false)
            } catch (e: Exception) {
                Log.e(TAG, "Échec de la mise à jour du statut de présence pour l'utilisateur $userId", e)
            }
            try {
                val tokenUpdate = mapOf("fcmToken" to FieldValue.delete())
                usersCollection.document(userId).update(tokenUpdate).await()
                Log.i(TAG, "Jeton FCM supprimé avec succès pour l'utilisateur $userId lors de la déconnexion.")
            } catch (e: Exception) {
                Log.e(TAG, "Échec de la suppression du jeton FCM pour l'utilisateur $userId", e)
            }
        }
        firebaseAuth.signOut()
    }

    override suspend fun linkGoogleAccount(pendingCredential: AuthCredential, email: String, password: String): AuthResultWrapper {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user!!
            val result = user.linkWithCredential(pendingCredential).await()
            AuthResultWrapper.Success(result.user)
        } catch (e: Exception) {
            val errorCode = (e as? FirebaseAuthException)?.errorCode
            AuthResultWrapper.Error(e, errorCode)
        }
    }

    // === NOUVELLE FONCTION HELPER PRIVÉE ===
    private suspend fun updateFcmToken(userId: String) {
        try {
            val token = firebaseMessaging.token.await()
            userProfileRepository.updateUserFCMToken(userId, token)
            Log.i(TAG, "Jeton FCM mis à jour avec succès pour l'utilisateur $userId lors de la connexion.")
        } catch (e: Exception) {
            Log.e(TAG, "Échec de la récupération ou de la mise à jour du jeton FCM pour $userId", e)
        }
    }
}