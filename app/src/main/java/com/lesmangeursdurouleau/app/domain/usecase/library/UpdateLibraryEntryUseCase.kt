// PRÊT À COLLER - Créez un nouveau fichier UpdateLibraryEntryUseCase.kt
package com.lesmangeursdurouleau.app.domain.usecase.library

import com.lesmangeursdurouleau.app.data.model.UserLibraryEntry
import com.lesmangeursdurouleau.app.data.repository.BookRepository
import com.lesmangeursdurouleau.app.utils.Resource
import javax.inject.Inject

/**
 * Use Case pour mettre à jour une entrée dans la bibliothèque personnelle d'un utilisateur.
 *
 * Encapsule la logique de sauvegarde des modifications, comme la mise à jour de la page
 * actuelle ou le changement de statut du livre (par exemple, de 'EN_COURS' à 'TERMINE').
 */
class UpdateLibraryEntryUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    /**
     * Exécute le cas d'utilisation.
     * @param userId L'identifiant de l'utilisateur.
     * @param entry L'objet [UserLibraryEntry] contenant les données mises à jour.
     * @return Un [Resource] indiquant le résultat de l'opération de sauvegarde.
     */
    suspend operator fun invoke(userId: String, entry: UserLibraryEntry): Resource<Unit> {
        // Pour l'instant, on délègue directement au repository.
        // On pourrait ajouter ici des logiques de validation complexes à l'avenir.
        return bookRepository.updateLibraryEntry(userId, entry)
    }
}