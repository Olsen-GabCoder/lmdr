// PRÊT À COLLER - Créez un nouveau fichier AdminRepository.kt dans le package data/repository
package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.utils.Resource

/**
 * Repository pour les actions réservées aux administrateurs.
 */
interface AdminRepository {

    /**
     * Appelle la Cloud Function pour définir un utilisateur comme administrateur.
     * @param email L'e-mail de l'utilisateur à promouvoir.
     * @return Un Resource indiquant le succès ou l'échec de l'opération, avec un message du serveur.
     */
    suspend fun setUserAsAdmin(email: String): Resource<String>
}