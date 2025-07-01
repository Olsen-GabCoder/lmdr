// PRÊT À COLLER - Remplacez tout le contenu de votre fichier MonthlyReading.kt par ceci.
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Représente une lecture mensuelle planifiée par le club.
 *
 * @property id L'identifiant unique du document Firestore.
 * @property bookId La référence vers l'ID du livre concerné.
 * @property year L'année de la lecture.
 * @property month Le mois de la lecture (1 pour Janvier, 12 pour Décembre).
 * @property analysisPhase Les détails de la phase d'analyse.
 * @property debatePhase Les détails de la phase de débat.
 * @property customDescription Une description ou une citation ajoutée par l'animateur.
 * @property createdAt Timestamp de la création du document.
 * @property updatedAt Timestamp de la dernière mise à jour du document.
 */
data class MonthlyReading(
    @get:Exclude val id: String = "",
    val bookId: String = "",
    val year: Int = 0,
    val month: Int = 0,
    // MODIFIÉ: Le constructeur par défaut de Phase utilise déjà le nouvel enum PhaseStatus.PLANIFIED.
    // Le code reste identique mais son comportement sous-jacent est maintenant plus robuste.
    val analysisPhase: Phase = Phase(status = PhaseStatus.PLANIFIED),
    val debatePhase: Phase = Phase(status = PhaseStatus.PLANIFIED),
    val customDescription: String? = null,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)