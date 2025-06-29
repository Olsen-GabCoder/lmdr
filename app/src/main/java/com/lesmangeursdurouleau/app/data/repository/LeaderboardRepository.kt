// PRÊT À COLLER - Fichier complet
package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository dédié à la récupération des données du classement d'affinité.
 */
interface LeaderboardRepository {

    /**
     * Récupère en temps réel le classement des conversations avec le plus haut score d'affinité.
     * @param limit Le nombre maximum d'entrées à récupérer (ex: 20 pour un Top 20).
     * @return Un Flow de Resource contenant la liste des conversations classées.
     */
    fun getAffinityLeaderboard(limit: Long): Flow<Resource<List<Conversation>>>
}