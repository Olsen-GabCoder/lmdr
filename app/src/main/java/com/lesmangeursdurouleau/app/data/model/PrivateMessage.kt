// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PrivateMessage.kt
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Énumération des statuts possibles pour un message.
 * SENT: Le message a été envoyé avec succès au serveur.
 * READ: Le message a été lu par le destinataire.
 */
enum class MessageStatus {
    SENT,
    READ
}

/**
 * Contient les informations nécessaires pour afficher une citation de réponse.
 * @property repliedToMessageId L'ID du message original auquel on répond.
 * @property repliedToSenderName Le nom de l'auteur du message original.
 * @property repliedToMessagePreview Un court extrait du texte ou un placeholder pour l'image du message original.
 */
data class ReplyInfo(
    val repliedToMessageId: String = "",
    val repliedToSenderName: String = "",
    val repliedToMessagePreview: String = ""
) {
    // Constructeur sans argument requis par Firestore
    constructor() : this("", "", "")
}


/**
 * Représente un message unique au sein d'une conversation privée.
 */
data class PrivateMessage(
    @DocumentId
    val id: String? = null,

    val senderId: String = "",

    // === DÉBUT DE L'AJOUT ===
    // JUSTIFICATION: Dénormalisation des données de l'expéditeur pour optimiser l'affichage
    // et éviter des lectures supplémentaires côté client.
    val senderUsername: String = "...",
    val senderProfilePictureUrl: String? = null,
    // === FIN DE L'AJOUT ===

    val text: String? = null,

    val imageUrl: String? = null,

    @ServerTimestamp
    val timestamp: Date? = null,

    val reactions: Map<String, String> = emptyMap(),

    val isEdited: Boolean = false,

    val status: String = MessageStatus.SENT.name,

    // Champ contenant les informations de réponse, s'il y en a.
    val replyInfo: ReplyInfo? = null

) {
    // Constructeur sans argument requis par Firestore pour la désérialisation
    constructor() : this(
        id = null,
        senderId = "",
        senderUsername = "...", // Ajouté
        senderProfilePictureUrl = null, // Ajouté
        text = null,
        imageUrl = null,
        timestamp = null,
        reactions = emptyMap(),
        isEdited = false,
        status = MessageStatus.SENT.name,
        replyInfo = null
    )
}