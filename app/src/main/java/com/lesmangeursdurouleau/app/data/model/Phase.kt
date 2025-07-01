// PRÊT À COLLER - Remplacez tout le contenu de votre fichier Phase.kt par ceci.
package com.lesmangeursdurouleau.app.data.model

import java.util.Date

/**
 * Représente une étape d'une lecture mensuelle (par exemple, analyse ou débat).
 *
 * @property date La date planifiée pour la phase.
 * @property status Le statut actuel de la phase, utilisant l'enum PhaseStatus pour la robustesse.
 * @property meetingLink Un lien optionnel vers une réunion en ligne pour cette phase.
 */
data class Phase(
    val date: Date? = null,
    // MODIFIÉ: Le type est maintenant l'enum PhaseStatus, avec une valeur par défaut sécurisée.
    val status: PhaseStatus = PhaseStatus.PLANIFIED,
    val meetingLink: String? = null
)

// SUPPRIMÉ: Le companion object avec les constantes String est désormais inutile.