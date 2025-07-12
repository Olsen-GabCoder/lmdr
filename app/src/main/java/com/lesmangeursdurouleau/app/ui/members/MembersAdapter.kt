// NOUVEAU FICHIER : app/src/main/java/com/lesmangeursdurouleau/app/ui/members/MembersAdapter.kt

package com.lesmangeursdurouleau.app.ui.members

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.ItemMemberBinding

/**
 * JUSTIFICATION DE L'EXTRACTION : La classe est extraite dans son propre fichier pour respecter
 * les bonnes pratiques de structure de projet, améliorer la lisibilité et permettre sa réutilisation
 * et ses tests unitaires de manière indépendante.
 *
 * JUSTIFICATION DE LA MODIFICATION : L'héritage a été changé de `ListAdapter` à `PagingDataAdapter`.
 * C'est le changement technique clé qui permet à l'adapter de consommer nativement le flux de
 * données paginées (`PagingData`) fourni par la librairie Paging 3. Il gère automatiquement
 * le cycle de vie de la pagination, les états de chargement et l'affichage des nouveaux items.
 */
class MembersAdapter(
    private val onItemClick: (User) -> Unit
) : PagingDataAdapter<User, MembersAdapter.MemberViewHolder>(UserDiffCallback()) {

    inner class MemberViewHolder(private val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(member: User?) {
            // Le membre peut être null pendant que Paging charge les données (placeholders)
            if (member == null) return

            binding.tvMemberUsername.text = member.username.takeIf { it.isNotEmpty() } ?: itemView.context.getString(R.string.username_not_defined)
            // FAILle DE SÉCURITÉ 🛡️ : L'affichage de l'email sera traité dans la prochaine tâche.
            binding.tvMemberEmail.text = member.email.takeIf { it.isNotEmpty() } ?: itemView.context.getString(R.string.na)

            Glide.with(itemView)
                .load(member.profilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .circleCrop()
                .into(binding.ivMemberPicture)

            itemView.setOnClickListener {
                // getItem peut retourner null avec PagingDataAdapter, on ajoute une vérification.
                getItem(bindingAdapterPosition)?.let { user ->
                    onItemClick(user)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = getItem(position)
        holder.bind(member)
    }
}