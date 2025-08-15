// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier Conversation.kt
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Représente une conversation privée entre deux utilisateurs.
 * Conçu avec une dénormalisation des données des participants (nom, photo)
 * pour optimiser les performances d'affichage de la liste des conversations.
 */
data class Conversation(
    @DocumentId
    val id: String? = null,

    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotoUrls: Map<String, String> = emptyMap(),

    val activeParticipantIds: List<String> = emptyList(),

    val lastMessage: String? = null,

    val lastMessageSenderId: String? = null,

    @ServerTimestamp
    val lastMessageTimestamp: Date? = null,

    // === DÉBUT DE L'AJOUT ===
    /**
     * Le statut du dernier message ("SENT" ou "READ").
     * Ce champ est dénormalisé depuis le document du dernier message par une Cloud Function.
     */
    val lastMessageStatus: String? = null,
    // === FIN DE L'AJOUT ===

    val unreadCount: Map<String, Int> = emptyMap(),

    val typingStatus: Map<String, Boolean> = emptyMap(),

    @get:PropertyName("isFavorite")
    var isFavorite: Boolean = false,

    val affinityScore: Int = 0,

    @ServerTimestamp
    val lastInteractionTimestamp: Date? = null,

    @ServerTimestamp
    val lastTierUpgradeTimestamp: Date? = null,

    @ServerTimestamp
    val firstMessageTimestamp: Date? = null,

    val totalMessageCount: Long = 0,

    @get:PropertyName("isStreakActive")
    val isStreakActive: Boolean = false,

    val completedChallengeIds: List<String> = emptyList(),

    @get:PropertyName("isPinned")
    val isPinned: Boolean = false,

    @get:PropertyName("isArchived")
    val isArchived: Boolean = false
) {
    // Constructeur sans argument requis par Firestore pour la désérialisation.
    constructor() : this(
        id = null,
        participantIds = emptyList(),
        participantNames = emptyMap(),
        participantPhotoUrls = emptyMap(),
        activeParticipantIds = emptyList(),
        lastMessage = null,
        lastMessageSenderId = null,
        lastMessageTimestamp = null,
        lastMessageStatus = null,
        unreadCount = emptyMap(),
        typingStatus = emptyMap(),
        isFavorite = false,
        affinityScore = 0,
        lastInteractionTimestamp = null,
        lastTierUpgradeTimestamp = null,
        firstMessageTimestamp = null,
        totalMessageCount = 0,
        isStreakActive = false,
        completedChallengeIds = emptyList(),
        isPinned = false,
        isArchived = false
    )
}