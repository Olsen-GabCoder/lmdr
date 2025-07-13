// PRÊT À COLLER - Fichier FirebaseConstants.kt mis à jour
package com.lesmangeursdurouleau.app.remote

object FirebaseConstants {
    // --- COLLECTIONS PRINCIPALES ---
    const val COLLECTION_USERS = "users"
    const val COLLECTION_BOOKS = "books"
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
    const val FIELD_LIKES_COUNT = "likesCount"

    // --- SOUS-COLLECTIONS ---
    const val SUBCOLLECTION_USER_READINGS = "user_readings"
    const val SUBCOLLECTION_COMPLETED_READINGS = "completed_readings"
    const val SUBCOLLECTION_COMMENTS = "comments"
    const val SUBCOLLECTION_LIKES = "likes"
    const val SUBCOLLECTION_MESSAGES = "messages"
    const val SUBCOLLECTION_ACTIVE_READING_LIKES = "likes"


    // --- CHEMINS FIREBASE STORAGE ---
    // JUSTIFICATION DE L'AJOUT : Centralisation des chemins de stockage pour éviter les erreurs
    // de frappe et faciliter la maintenance. Chaque type d'image a son propre chemin racine.
    const val STORAGE_PROFILE_PICTURES = "profile_pictures"
    const val STORAGE_COVER_PICTURES = "cover_pictures"
    const val STORAGE_CHAT_IMAGES = "chat_images"
}