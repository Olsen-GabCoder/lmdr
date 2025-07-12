// NOUVEAU FICHIER : app/src/main/java/com/lesmangeursdurouleau/app/ui/members/UserDiffCallback.kt

package com.lesmangeursdurouleau.app.ui.members

import androidx.recyclerview.widget.DiffUtil
import com.lesmangeursdurouleau.app.data.model.User

/**
 * JUSTIFICATION DE L'EXTRACTION : La classe est extraite dans son propre fichier pour une meilleure
 * organisation et séparation des préoccupations. C'est une classe utilitaire qui ne doit pas
 * être imbriquée dans un Fragment.
 */
class UserDiffCallback : DiffUtil.ItemCallback<User>() {
    override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem.uid == newItem.uid
    }

    override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem == newItem
    }
}