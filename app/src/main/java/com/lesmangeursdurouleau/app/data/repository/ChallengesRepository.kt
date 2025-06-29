// PRÊT À COLLER - Fichier complet
package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Challenge
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository dédié à la gestion des défis hebdomadaires.
 */
interface ChallengesRepository {

    /**
     * Récupère en temps réel la liste des défis de la semaine.
     * Ces défis sont stockés dans un document unique pour une performance optimale.
     * @return Un Flow de Resource contenant la liste des défis.
     */
    fun getWeeklyChallenges(): Flow<Resource<List<Challenge>>>
}