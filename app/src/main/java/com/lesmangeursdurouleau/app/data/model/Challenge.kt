// PRÊT À COLLER - Fichier complet
package com.lesmangeursdurouleau.app.data.model

import android.os.Parcelable
import com.google.firebase.firestore.DocumentId
import kotlinx.parcelize.Parcelize

/**
 * Représente un défi unique.
 * @property id L'ID unique du défi, qui sera généré par notre Cloud Function.
 * @property title La description du défi, ex: "Souhaitez-lui une bonne lecture 📖".
 * @property bonusPoints Le nombre de points d'affinité bonus que ce défi rapporte.
 */
// === DÉBUT DE LA MODIFICATION ===
@Parcelize
data class Challenge(
    // Note: L'@DocumentId n'est pas utilisé ici car les défis sont dans un tableau
    // et non des documents individuels, mais l'ID reste essentiel.
    val id: String = "",
    val title: String = "",
    val bonusPoints: Int = 0
) : Parcelable { // <-- AJOUT DE L'HÉRITAGE
// === FIN DE LA MODIFICATION ===

    // Un constructeur vide est utile pour certains cas de désérialisation,
    // bien que pour un tableau, il soit moins critique.
    constructor() : this("", "", 0)
}