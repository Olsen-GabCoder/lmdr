// PRÊT À COLLER - Fichier NotificationType.kt
package com.lesmangeursdurouleau.app.data.model // ou com.lesmangeursdurouleau.app.notifications.model

/**
 * Définit de manière stricte tous les types d'événements pouvant générer une notification.
 * Utilisé à la fois par le backend (Cloud Functions) pour taguer les notifications
 * et par le client pour déterminer comment afficher et gérer chaque notification.
 */
enum class NotificationType {
    // Notifications sociales (ce que nous ajoutons)
    NEW_FOLLOWER,
    LIKE_ON_READING,
    COMMENT_ON_READING,

    // Notifications générales (déjà gérées par le FCM Service, mais centralisées ici)
    NEW_MONTHLY_READING,
    PHASE_REMINDER,
    PHASE_STATUS_CHANGE,
    MEETING_LINK_UPDATE,

    // Notifications de messagerie (déjà gérées)
    NEW_PRIVATE_MESSAGE,

    // Notifications de récompenses (déjà gérées)
    TIER_UPGRADE,

    // Type par défaut pour les cas inconnus ou les notifications simples
    UNKNOWN
}