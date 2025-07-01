// PRÊT À COLLER - Nouveau Fichier
package com.lesmangeursdurouleau.app.data.model

/**
 * Définit les statuts possibles pour une phase d'une lecture mensuelle.
 * L'utilisation d'un enum garantit la sécurité de type et prévient les erreurs
 * liées à l'utilisation de chaînes de caractères brutes ("magic strings").
 */
enum class PhaseStatus {
    PLANIFIED,
    IN_PROGRESS,
    COMPLETED
}