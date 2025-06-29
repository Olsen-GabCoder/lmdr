package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Représente une entrée unique dans le classement hebdomadaire.
 * Contient les informations sur un binôme et son score.
 */
data class WeeklyWinner(
    val conversationId: String = "",
    val score: Int = 0,
    val participantNames: Map<String, String> = emptyMap(),
    val participantIds: List<String> = emptyList(),
    @ServerTimestamp
    val timestamp: Date? = null
)

/**
 * Représente le document principal contenant la liste des gagnants pour une semaine donnée.
 */
data class Leaderboard(
    @ServerTimestamp
    val createdAt: Date? = null,
    val winners: List<WeeklyWinner> = emptyList()
)