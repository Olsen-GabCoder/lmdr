// PRÊT À COLLER - Remplacez tout le contenu de votre fichier CompletedReading.kt par ceci.
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Modèle de données normalisé pour représenter une lecture terminée par un utilisateur.
 * Ne contient que les informations de la relation, pas les détails du livre.
 * Stocké dans une sous-collection 'completed_readings' sous le document de l'utilisateur.
 *
 * @param bookId L'ID unique du livre (clé étrangère vers la collection 'books'). C'EST LA SOURCE DE VÉRITÉ.
 * @param userId L'ID de l'utilisateur qui a terminé ce livre.
 * @param completionDate La date à laquelle la lecture a été marquée comme terminée. Sera générée par le serveur.
 */
data class CompletedReading(
    var bookId: String = "",
    var userId: String = "",
    @ServerTimestamp
    var completionDate: Date? = null
    // SUPPRIMÉ: title, author, coverImageUrl, totalPages. Ces champs provoquaient une duplication de données.
)