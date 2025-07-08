// PRÊT À COLLER - Fichier NotificationRepository.kt
package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Notification
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository dédié à la gestion des notifications de l'utilisateur.
 */
interface NotificationRepository {

    /**
     * Récupère en temps réel la liste des notifications pour un utilisateur donné.
     * Les notifications sont triées par ordre antéchronologique.
     *
     * @param userId L'ID de l'utilisateur dont on veut récupérer les notifications.
     * @return Un Flow de Resource contenant la liste des notifications.
     */
    fun getNotifications(userId: String): Flow<Resource<List<Notification>>>

    /**
     * Marque une notification spécifique comme lue.
     *
     * @param userId L'ID de l'utilisateur propriétaire de la notification.
     * @param notificationId L'ID de la notification à marquer comme lue.
     * @return Une Resource indiquant le succès ou l'échec de l'opération.
     */
    suspend fun markNotificationAsRead(userId: String, notificationId: String): Resource<Unit>

    /**
     * Marque toutes les notifications non lues d'un utilisateur comme lues.
     *
     * @param userId L'ID de l'utilisateur.
     * @return Une Resource indiquant le succès ou l'échec.
     */
    suspend fun markAllNotificationsAsRead(userId: String): Resource<Unit>
}