package com.lesmangeursdurouleau.app.data.model

/**
 * JUSTIFICATION DE LA CRÉATION :
 * Ce nouveau modèle de données est au cœur de l'amélioration de la liste des membres.
 * Il est conçu pour être plus riche que `UserListItem` mais beaucoup plus léger que
 * l'objet `User` complet.
 *
 * Il contient toutes les informations nécessaires pour afficher la nouvelle carte de membre,
 * y compris les statistiques et l'état de suivi, tout en évitant de charger des
 * données lourdes ou inutiles (comme le fcmToken, la bio complète, etc.).
 *
 * L'utilisation de ce modèle dans la pagination garantira que les listes de membres
 * restent performantes et scalables.
 */
data class EnrichedUserListItem(
    val uid: String = "",
    val username: String = "",
    val profilePictureUrl: String? = null,
    val city: String? = null,
    val booksReadCount: Int = 0,
    val isOnline: Boolean = false,
    // Ce champ sera calculé dynamiquement pour chaque utilisateur connecté.
    val isFollowedByCurrentUser: Boolean = false
)