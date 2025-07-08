// PRÊT À COLLER - Fichier FirebaseConstants.kt complet et MODIFIÉ
package com.lesmangeursdurouleau.app.remote

object FirebaseConstants {
    // --- COLLECTIONS PRINCIPALES ---
    const val COLLECTION_USERS = "users"
    const val COLLECTION_BOOKS = "books"
    const val COLLECTION_GENERAL_CHAT = "general_chat_messages"
    const val COLLECTION_MONTHLY_READINGS = "monthly_readings"
    const val COLLECTION_APP_CONFIG = "app_config"
    const val COLLECTION_CONVERSATIONS = "conversations"
    const val COLLECTION_CHALLENGES = "challenges"

    // --- DOCUMENTS SPÉCIFIQUES ---
    const val DOCUMENT_PERMISSIONS = "permissions"
    const val DOCUMENT_ACTIVE_READING = "activeReading"

    // --- CHAMPS SPÉCIFIQUES ---
    const val FIELD_EDIT_READINGS_CODE = "edit_readings_code"
    const val FIELD_SECRET_CODE_LAST_UPDATED_TIMESTAMP = "lastSecretCodeUpdateTimestamp"
    // AJOUT : Champ pour le compteur de likes sur un livre
    const val FIELD_LIKES_COUNT = "likesCount"

    // --- SOUS-COLLECTIONS ---
    const val SUBCOLLECTION_USER_READINGS = "user_readings"
    const val SUBCOLLECTION_COMPLETED_READINGS = "completed_readings"
    const val SUBCOLLECTION_COMMENTS = "comments"
    const val SUBCOLLECTION_LIKES = "likes" // Gardé pour les "likes" de commentaires
    const val SUBCOLLECTION_MESSAGES = "messages"
    // AJOUT : Sous-collection pour les likes sur une lecture active
    const val SUBCOLLECTION_ACTIVE_READING_LIKES = "likes"


    // AJOUT: CHEMINS FIREBASE STORAGE
    const val STORAGE_CHAT_IMAGES = "chat_images"
}