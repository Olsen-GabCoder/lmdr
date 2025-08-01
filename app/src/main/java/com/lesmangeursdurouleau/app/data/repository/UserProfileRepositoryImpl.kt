// PRÊT À COLLER - Fichier UserProfileRepositoryImpl.kt mis à jour
package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.model.UserListItem
import com.lesmangeursdurouleau.app.data.paging.SearchUsersPagingSource
import com.lesmangeursdurouleau.app.data.paging.UsersPagingSource
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserProfileRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorageService: FirebaseStorageService
) : UserProfileRepository {

    companion object {
        private const val TAG = "UserProfileRepository"
        private const val USERS_PAGE_SIZE = 20
    }

    private val usersCollection = firestore.collection(FirebaseConstants.COLLECTION_USERS)

    private fun createUserFromSnapshot(document: DocumentSnapshot): User? {
        return try {
            document.toObject(User::class.java)?.copy(uid = document.id)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur de désérialisation pour le document User ${document.id}", e)
            null
        }
    }

    override suspend fun updateUserProfile(userId: String, username: String): Resource<Unit> {
        if (username.isBlank()) return Resource.Error("Le pseudo ne peut pas être vide.")
        return try {
            val user = firebaseAuth.currentUser
            if (user == null || user.uid != userId) return Resource.Error("Erreur d'authentification.")
            val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(username).build()
            user.updateProfile(profileUpdates).await()
            usersCollection.document(userId).update("username", username).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur lors de la mise à jour du profil: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String> {
        return try {
            val user = firebaseAuth.currentUser
            if (user == null || user.uid != userId) return Resource.Error("Erreur d'authentification.")

            val uploadResult = firebaseStorageService.uploadProfilePicture(userId, imageData)
            if (uploadResult is Resource.Success) {
                val photoUrl = uploadResult.data
                if (photoUrl.isNullOrBlank()) return Resource.Error("L'URL de la photo est vide.")

                usersCollection.document(userId).set(mapOf("profilePictureUrl" to photoUrl), SetOptions.merge()).await()
                val profileUpdates = UserProfileChangeRequest.Builder().setPhotoUri(photoUrl.toUri()).build()
                user.updateProfile(profileUpdates).await()

                Resource.Success(photoUrl)
            } else {
                Resource.Error(uploadResult.message ?: "Erreur lors de l'upload de la photo.")
            }
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    /**
     * JUSTIFICATION DE L'AJOUT : Implémentation de la nouvelle méthode pour la mise à jour de la photo de couverture.
     * La structure est symétrique à `updateUserProfilePicture` pour maintenir la cohérence.
     * 1. Vérifie l'authentification.
     * 2. Appelle une méthode dédiée `uploadCoverPicture` dans `FirebaseStorageService`. (NOTE : Cette méthode est supposée exister et doit être créée).
     * 3. Si l'upload réussit, met à jour le champ `coverPictureUrl` dans le document Firestore de l'utilisateur.
     * 4. Ne met PAS à jour le `photoUri` du profil Auth de Firebase, car il n'y a pas de champ pour une photo de couverture.
     * 5. Retourne le `Resource<String>` avec l'URL de la nouvelle couverture.
     */
    override suspend fun updateUserCoverPicture(userId: String, imageData: ByteArray): Resource<String> {
        return try {
            val user = firebaseAuth.currentUser
            if (user == null || user.uid != userId) return Resource.Error("Erreur d'authentification.")

            // ATTENTION : Ce code suppose l'existence de la méthode `uploadCoverPicture` dans le service.
            // Il ne compilera pas tant que cette méthode ne sera pas ajoutée à `FirebaseStorageService`.
            val uploadResult = firebaseStorageService.uploadCoverPicture(userId, imageData)

            if (uploadResult is Resource.Success) {
                val coverUrl = uploadResult.data
                if (coverUrl.isNullOrBlank()) return Resource.Error("L'URL de l'image de couverture est vide.")

                // Met à jour le champ `coverPictureUrl` dans Firestore.
                usersCollection.document(userId).set(mapOf("coverPictureUrl" to coverUrl), SetOptions.merge()).await()

                Resource.Success(coverUrl)
            } else {
                Resource.Error(uploadResult.message ?: "Erreur lors de l'upload de l'image de couverture.")
            }
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    @Deprecated("Utiliser getAllUsersPaginated pour une approche performante et scalable.", ReplaceWith("getAllUsersPaginated()"))
    override fun getAllUsers(): Flow<Resource<List<User>>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = usersCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur: ${error.localizedMessage}"))
                close(error); return@addSnapshotListener
            }
            if (snapshot != null) {
                val users = snapshot.documents.mapNotNull { doc ->
                    createUserFromSnapshot(doc)
                }
                trySend(Resource.Success(users))
            }
        }
        awaitClose { listener.remove() }
    }

    override fun getAllUsersPaginated(): Flow<PagingData<UserListItem>> {
        return Pager(
            config = PagingConfig(pageSize = USERS_PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { UsersPagingSource(firestore) }
        ).flow
    }

    override fun searchUsersPaginated(query: String): Flow<PagingData<UserListItem>> {
        return Pager(
            config = PagingConfig(pageSize = USERS_PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { SearchUsersPagingSource(firestore, query) }
        ).flow
    }

    override fun getUserById(userId: String): Flow<Resource<User>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(Resource.Error("ID utilisateur manquant.")); close(); return@callbackFlow
        }
        trySend(Resource.Loading())
        val listener = usersCollection.document(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Resource.Error("Erreur: ${error.localizedMessage}"))
                close(error); return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                createUserFromSnapshot(snapshot)?.let { user ->
                    trySend(Resource.Success(user))
                } ?: trySend(Resource.Error("Erreur de conversion des données utilisateur."))
            } else {
                trySend(Resource.Error("Utilisateur non trouvé."))
            }
        }
        awaitClose { listener.remove() }
    }

    override suspend fun updateUserTypingStatus(userId: String, isTyping: Boolean): Resource<Unit> {
        if (userId.isBlank()) return Resource.Error("ID utilisateur invalide.")
        return try {
            val typingUpdate = mapOf("isTypingInGeneralChat" to isTyping)
            usersCollection.document(userId).set(typingUpdate, SetOptions.merge()).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserPresence(userId: String, isOnline: Boolean) {
        if (userId.isBlank()) return
        try {
            val presenceUpdate = mapOf(
                "isOnline" to isOnline,
                "lastSeen" to FieldValue.serverTimestamp()
            )
            usersCollection.document(userId).update(presenceUpdate).await()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la mise à jour de la présence pour $userId", e)
        }
    }

    override suspend fun updateUserBio(userId: String, bio: String): Resource<Unit> {
        return try {
            if (firebaseAuth.currentUser?.uid != userId) return Resource.Error("Erreur d'authentification.")
            usersCollection.document(userId).update("bio", bio).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserCity(userId: String, city: String): Resource<Unit> {
        return try {
            if (firebaseAuth.currentUser?.uid != userId) return Resource.Error("Erreur d'authentification.")
            usersCollection.document(userId).update("city", city).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserEditPermission(userId: String, canEdit: Boolean): Resource<Unit> {
        return try {
            usersCollection.document(userId).update("canEditReadings", canEdit).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserLastPermissionTimestamp(userId: String, timestamp: Long?): Resource<Unit> {
        return try {
            val updateData = if (timestamp != null) {
                mapOf("lastPermissionGrantedTimestamp" to timestamp)
            } else {
                mapOf("lastPermissionGrantedTimestamp" to FieldValue.delete())
            }
            usersCollection.document(userId).set(updateData, SetOptions.merge()).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserFCMToken(userId: String, token: String): Resource<Unit> {
        if (userId.isBlank() || token.isBlank()) return Resource.Error("Informations manquantes.")
        return try {
            val tokenUpdate = mapOf("fcmToken" to token)
            usersCollection.document(userId).set(tokenUpdate, SetOptions.merge()).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }
}