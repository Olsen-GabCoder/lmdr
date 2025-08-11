package com.lesmangeursdurouleau.app.ui.members

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.EnrichedUserListItem
import com.lesmangeursdurouleau.app.databinding.ItemMemberBinding

class MembersAdapter(
    private val onItemClick: (EnrichedUserListItem) -> Unit,
    private val onFollowClick: (EnrichedUserListItem) -> Unit,
    private val onUnfollowClick: (EnrichedUserListItem) -> Unit
) : PagingDataAdapter<EnrichedUserListItem, MembersAdapter.MemberViewHolder>(EnrichedUserListItemDiffCallback()) {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    inner class MemberViewHolder(private val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                getItem(bindingAdapterPosition)?.let { user ->
                    onItemClick(user)
                }
            }
            binding.btnFollow.setOnClickListener {
                getItem(bindingAdapterPosition)?.let { user ->
                    if (user.isFollowedByCurrentUser) {
                        onUnfollowClick(user)
                    } else {
                        onFollowClick(user)
                    }
                }
            }
        }

        fun bind(member: EnrichedUserListItem?) {
            if (member == null) return

            binding.tvMemberUsername.text = member.username
            Glide.with(itemView.context)
                .load(member.profilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .circleCrop()
                .into(binding.ivMemberPicture)
            binding.ivMemberPicture.contentDescription = itemView.context.getString(R.string.profile_picture_of_user_description, member.username)

            binding.tvMemberCity.isVisible = !member.city.isNullOrBlank()
            binding.tvMemberCity.text = member.city

            binding.tvBooksReadCount.text = itemView.context.resources.getQuantityString(
                R.plurals.books_read_count_format, member.booksReadCount, member.booksReadCount
            )

            val isOnline = member.isOnline
            binding.ivOnlineIndicator.isVisible = isOnline
            binding.tvOnlineStatus.isVisible = isOnline

            if (member.uid == currentUserId) {
                binding.btnFollow.isVisible = false
            } else {
                binding.btnFollow.isVisible = true
                updateFollowButton(member.isFollowedByCurrentUser)
            }
        }

        private fun updateFollowButton(isFollowing: Boolean) {
            val context = itemView.context
            if (isFollowing) {
                binding.btnFollow.text = context.getString(R.string.followed)
                binding.btnFollow.icon = ContextCompat.getDrawable(context, R.drawable.ic_check_single)
                // === DÉBUT DE LA MODIFICATION ===
                // JUSTIFICATION : Utilisation de la couleur primaire de votre thème pour le bouton "Suivi",
                // conformément à la maquette et pour une meilleure cohérence visuelle.
                val colorPrimary = ContextCompat.getColor(context, R.color.primary_accent)
                binding.btnFollow.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                binding.btnFollow.setTextColor(ContextCompat.getColor(context, R.color.white))
                binding.btnFollow.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
                binding.btnFollow.strokeWidth = 0 // Pas de contour pour le bouton plein
                // === FIN DE LA MODIFICATION ===
            } else {
                binding.btnFollow.text = context.getString(R.string.follow)
                binding.btnFollow.icon = null
                val colorPrimary = ContextCompat.getColor(context, R.color.primary_accent)
                binding.btnFollow.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.transparent))
                binding.btnFollow.setTextColor(colorPrimary)
                binding.btnFollow.strokeColor = ColorStateList.valueOf(colorPrimary)
                binding.btnFollow.strokeWidth = (1 * context.resources.displayMetrics.density).toInt() // 1dp de contour
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}