// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PrivateMessage.kt
package com.lesmangeursdurouleau.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class MessageStatus {
    SENT,
    READ
}

data class ReplyInfo(
    val repliedToMessageId: String = "",
    val repliedToSenderName: String = "",
    val repliedToMessagePreview: String = ""
) {
    constructor() : this("", "", "")
}

data class PrivateMessage(
    @DocumentId
    val id: String? = null,
    val senderId: String = "",
    val senderUsername: String = "...",
    val senderProfilePictureUrl: String? = null,
    val text: String? = null,
    val imageUrl: String? = null,
    @ServerTimestamp
    val timestamp: Date? = null,
    val reactions: Map<String, String> = emptyMap(),
    val isEdited: Boolean = false,
    val status: String = MessageStatus.SENT.name,
    val replyInfo: ReplyInfo? = null,

    // === DÉBUT DE LA CORRECTION : Ajout de l'annotation PropertyName ===
    // Rôle: Force Firestore à utiliser "isForwarded" comme nom de champ,
    // évitant la conversion automatique en "forwarded" qui créait l'incohérence.
    @get:PropertyName("isForwarded")
    val isForwarded: Boolean = false
    // === FIN DE LA CORRECTION ===
) {
    // Constructeur sans argument requis par Firestore pour la désérialisation
    constructor() : this(
        id = null,
        senderId = "",
        senderUsername = "...",
        senderProfilePictureUrl = null,
        text = null,
        imageUrl = null,
        timestamp = null,
        reactions = emptyMap(),
        isEdited = false,
        status = MessageStatus.SENT.name,
        replyInfo = null,
        isForwarded = false
    )
}