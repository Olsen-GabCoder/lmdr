// Fichier Modifié : app/src/main/java/com/lesmangeursdurouleau/app/ui/members/MembersAdapter.kt

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

class MembersAdapter(
    private val onItemClick: (User) -> Unit
) : PagingDataAdapter<User, MembersAdapter.MemberViewHolder>(UserDiffCallback()) {

    inner class MemberViewHolder(private val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(member: User?) {
            if (member == null) return

            binding.tvMemberUsername.text = member.username.takeIf { it.isNotEmpty() } ?: itemView.context.getString(R.string.username_not_defined)

            // JUSTIFICATION DE LA SUPPRESSION : La ligne de code qui liait l'e-mail de l'utilisateur
            // a été supprimée car le TextView correspondant (`tv_member_email`) a été retiré du
            // layout `item_member.xml` pour corriger la faille de sécurité 🛡️.
            // binding.tvMemberEmail.text = member.email.takeIf { it.isNotEmpty() } ?: itemView.context.getString(R.string.na)

            Glide.with(itemView)
                .load(member.profilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .circleCrop()
                .into(binding.ivMemberPicture)

            itemView.setOnClickListener {
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