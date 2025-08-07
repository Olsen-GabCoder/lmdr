// PRÊT À COLLER - Créez un nouveau fichier SetUserAdminUseCase.kt (par ex. dans le package domain/usecase/admin)
package com.lesmangeursdurouleau.app.domain.usecase.admin

import com.lesmangeursdurouleau.app.data.repository.AdminRepository
import com.lesmangeursdurouleau.app.utils.Resource
import javax.inject.Inject

/**
 * Use Case pour promouvoir un utilisateur au rang d'administrateur.
 * Encapsule la logique métier de cette action critique.
 */
class SetUserAdminUseCase @Inject constructor(
    private val adminRepository: AdminRepository
) {
    /**
     * Exécute le cas d'utilisation.
     * @param email L'adresse e-mail de l'utilisateur à promouvoir.
     * @return Un [Resource] contenant un message de succès ou d'erreur.
     */
    suspend operator fun invoke(email: String): Resource<String> {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return Resource.Error("Veuillez fournir une adresse e-mail valide.")
        }
        return adminRepository.setUserAsAdmin(email)
    }
}