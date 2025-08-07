// PRÊT À COLLER - Créez un nouveau fichier Role.kt dans le package data/model
package com.lesmangeursdurouleau.app.data.model

/**
 * Définit les rôles possibles pour un utilisateur au sein de l'application.
 * L'utilisation d'un enum garantit la sécurité de type et la cohérence.
 */
enum class Role {
    USER,       // Rôle par défaut pour tous les membres.
    ADMIN       // Rôle pour les administrateurs avec des permissions étendues.
}