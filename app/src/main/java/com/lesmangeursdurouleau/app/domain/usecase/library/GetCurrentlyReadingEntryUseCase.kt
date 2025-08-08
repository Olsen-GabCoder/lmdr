// PRÊT À COLLER - Créez un nouveau fichier GetCurrentlyReadingEntryUseCase.kt dans domain/usecase/library
package com.lesmangeursdurouleau.app.domain.usecase.library

import com.lesmangeursdurouleau.app.data.model.ReadingStatus
import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use Case pour récupérer l'entrée de bibliothèque unique qui est actuellement
 * en cours de lecture par un utilisateur.
 */
class GetCurrentlyReadingEntryUseCase @Inject constructor(
    private val getLibraryEntriesUseCase: GetLibraryEntriesUseCase
) {
    /**
     * Exécute le cas d'utilisation.
     * @param userId L'identifiant de l'utilisateur.
     * @return Un [Flow] de [Resource] qui émet l'objet [UserLibraryEntry] en cours de lecture,
     * ou null si aucune n'est trouvée.
     */
    operator fun invoke(userId: String): Flow<Resource<UserLibraryEntry?>> {
        return getLibraryEntriesUseCase(userId).map { resource ->
            when (resource) {
                is Resource.Success -> {
                    val currentlyReadingEntry = resource.data?.find { it.status == ReadingStatus.READING }
                    Resource.Success(currentlyReadingEntry)
                }
                is Resource.Error -> Resource.Error(resource.message ?: "Erreur inconnue")
                is Resource.Loading -> Resource.Loading()
            }
        }
    }
}