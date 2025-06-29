// app/src/main/java/com/lesmangeursdurouleau/app/data/model/UserLibraryEntry.kt
package com.lesmangeursdurouleau.app.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize

/**
 * Définit les différents statuts de lecture d'un livre dans la bibliothèque d'un utilisateur.
 */
enum class ReadingStatus {
    TO_READ,  // Le livre a été ajouté à la bibliothèque mais n'a pas encore été commencé.
    READING,  // Le livre est en cours de lecture.
    FINISHED  // Le livre a été entièrement lu.
}

/**
 * Représente une entrée unique dans la bibliothèque personnelle d'un utilisateur.
 * Ce modèle unifié remplace les anciens modèles séparés pour les lectures en cours et terminées.
 *
 * @property bookId L'identifiant du livre (correspond à un document dans la collection "books").
 * @property userId L'identifiant de l'utilisateur propriétaire de cette entrée.
 * @property status Le statut actuel de lecture du livre (À lire, En cours, Terminé).
 * @property currentPage La dernière page lue par l'utilisateur. Essentiel pour le suivi de progression.
 * @property totalPages Le nombre total de pages du livre. Dénormalisé pour des calculs de % rapides.
 * @property addedDate Timestamp automatique de l'ajout du livre à la bibliothèque.
 * @property lastReadDate Timestamp de la dernière mise à jour de la progression de lecture.
 */
@Parcelize
data class UserLibraryEntry(
    val bookId: String = "",
    val userId: String = "",
    var status: ReadingStatus = ReadingStatus.TO_READ,
    var currentPage: Int = 0,
    val totalPages: Int = 0, // Dénormalisé depuis l'objet Book pour la performance
    @ServerTimestamp val addedDate: Timestamp? = null,
    var lastReadDate: Timestamp? = null
) : Parcelable