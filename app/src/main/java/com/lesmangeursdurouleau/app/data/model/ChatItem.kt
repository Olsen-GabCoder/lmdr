// PRÊT À COLLER - Remplacez le contenu de votre fichier qui définit ChatItem.
package com.lesmangeursdurouleau.app.data.model

import java.util.Date

sealed interface ChatItem {
    val id: String
    val timestamp: Date?
}

// Enveloppe pour un message, implémentant ChatItem
data class MessageItem(
    val message: PrivateMessage
) : ChatItem {
    override val id: String
        get() = message.id ?: "temp_${message.timestamp?.time}" // Assure un ID non-nul
    override val timestamp: Date?
        get() = message.timestamp
}

// Classe pour un séparateur de date
data class DateSeparatorItem(
    override val timestamp: Date
) : ChatItem {
    // L'ID est basé sur le jour pour être unique par jour
    override val id: String = "separator_${timestamp.time / (1000 * 60 * 60 * 24)}"
}

// NOUVEAU : Classe pour l'en-tête de bloc de messages
data class HeaderItem(
    val senderName: String,
    override val timestamp: Date,
    // ID unique basé sur le premier message du bloc pour la stabilité de DiffUtil
    val messageBlockId: String
) : ChatItem {
    override val id: String = "header_$messageBlockId"
}

// ======================= DÉBUT DE L'AJOUT =======================
// JUSTIFICATION: Ajout de l'objet manquant pour l'indicateur de chargement.
// Il respecte le contrat de votre interface ChatItem.
object LoadingIndicatorItem : ChatItem {
    override val id: String = "loading_indicator"
    override val timestamp: Date? = null // Le timestamp n'est pas pertinent pour cet item.
}
// ======================== FIN DE L'AJOUT ========================