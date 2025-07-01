// PRÊT À COLLER - Nouveau Fichier
package com.lesmangeursdurouleau.app.domain.usecase.permissions

import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.repository.AppConfigRepository
import com.lesmangeursdurouleau.app.data.repository.UserProfileRepository
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Use Case dédié à la vérification de la permission d'édition d'un utilisateur.
 * Encapsule toute la logique de comparaison des timestamps et de validité de la permission.
 */
class CheckUserEditPermissionUseCase @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val appConfigRepository: AppConfigRepository,
    private val firebaseAuth: FirebaseAuth
) {
    private companion object {
        // Durée de validité de la permission en millisecondes (ex: 3 minutes)
        private const val PERMISSION_LIFESPAN_MILLIS = 3 * 60 * 1000L
    }

    /**
     * Exécute le cas d'utilisation.
     * @return Un Flow<Boolean> qui émet `true` si l'utilisateur a une permission active, `false` sinon.
     * Le Flow se met à jour automatiquement si le profil de l'utilisateur ou la configuration de l'admin change.
     */
    operator fun invoke(): Flow<Boolean> {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId.isNullOrBlank()) {
            return kotlinx.coroutines.flow.flowOf(false) // Si pas d'utilisateur, pas de permission.
        }

        // Combine les informations du profil de l'utilisateur et de la configuration de l'application.
        return userProfileRepository.getUserById(currentUserId)
            .combine(appConfigRepository.getSecretCodeLastUpdatedTimestamp()) { userResource, adminTimestampResource ->

                // Si l'une des sources est en chargement ou en erreur, on considère la permission comme invalide.
                if (userResource !is Resource.Success || adminTimestampResource !is Resource.Success) {
                    return@combine false
                }

                val user = userResource.data
                val adminTimestamp = adminTimestampResource.data

                // Vérification n°1: L'utilisateur a-t-il la permission de base dans son profil ?
                if (user?.canEditReadings != true) {
                    return@combine false
                }

                // Vérification n°2: Avons-nous un timestamp de quand la permission a été accordée ?
                val lastGrantedTimestamp = user.lastPermissionGrantedTimestamp
                if (lastGrantedTimestamp == null) {
                    return@combine false // Permission de base mais jamais formellement accordée via le code.
                }

                // Vérification n°3: La permission a-t-elle expiré ?
                val isExpiredByTime = (System.currentTimeMillis() - lastGrantedTimestamp) > PERMISSION_LIFESPAN_MILLIS
                if (isExpiredByTime) {
                    return@combine false
                }

                // Vérification n°4: L'admin a-t-il changé le code secret depuis que la permission a été accordée ?
                if (adminTimestamp != null && lastGrantedTimestamp < adminTimestamp) {
                    return@combine false
                }

                // Si toutes les vérifications passent, la permission est valide.
                return@combine true
            }
    }
}