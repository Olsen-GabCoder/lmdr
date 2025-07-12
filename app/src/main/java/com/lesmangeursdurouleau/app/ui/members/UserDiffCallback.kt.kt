// Fichier Renommé et Modifié : app/src/main/java/com/lesmangeursdurouleau/app/ui/members/UserListItemDiffCallback.kt

package com.lesmangeursdurouleau.app.ui.members

import androidx.recyclerview.widget.DiffUtil
import com.lesmangeursdurouleau.app.data.model.UserListItem

/**
 * JUSTIFICATION DE LA MODIFICATION : La classe a été renommée et mise à jour pour comparer
 * des objets `UserListItem` au lieu de `User`, en alignement avec la nouvelle architecture
 * de données optimisée pour la liste.
 */
class UserListItemDiffCallback : DiffUtil.ItemCallback<UserListItem>() {
    override fun areItemsTheSame(oldItem: UserListItem, newItem: UserListItem): Boolean {
        return oldItem.uid == newItem.uid
    }

    override fun areContentsTheSame(oldItem: UserListItem, newItem: UserListItem): Boolean {
        return oldItem == newItem
    }
}