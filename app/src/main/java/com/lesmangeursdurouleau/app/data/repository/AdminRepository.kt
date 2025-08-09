// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier AdminRepository.kt
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
    /**
     * Appelle la Cloud Function pour créer un livre avec ses fichiers de manière atomique.
     * @param title Le titre du livre.
     * @param author L'auteur du livre.
     * @param synopsis Le résumé du livre.
     * @param totalPages Le nombre total de pages.
     * @param coverImage Les données binaires de l'image de couverture.
     * @param pdfFile Les données binaires du fichier PDF.
     * @return Une Resource contenant l'ID du nouveau livre en cas de succès.
     */
    suspend fun createBookWithFiles(
        title: String,
        author: String,
        synopsis: String,
        totalPages: Int,
        coverImage: ByteArray?,
        pdfFile: ByteArray?
    ): Resource<String>
}