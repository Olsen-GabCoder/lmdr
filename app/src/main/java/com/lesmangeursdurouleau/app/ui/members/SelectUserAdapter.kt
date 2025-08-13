// CRÉEZ UN NOUVEAU FICHIER : ui/members/SelectUserAdapter.kt
package com.lesmangeursdurouleau.app.ui.members

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.ItemSelectUserBinding

class SelectUserAdapter(
    private val onUserClick: (User) -> Unit
) : ListAdapter<User, SelectUserAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemSelectUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position), onUserClick)
    }

    class UserViewHolder(private val binding: ItemSelectUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User, onUserClick: (User) -> Unit) {
            binding.tvUsername.text = user.username
            binding.onlineIndicator.isVisible = user.isOnline

            Glide.with(binding.root.context)
                .load(user.profilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(binding.ivUserPhoto)

            binding.root.setOnClickListener {
                onUserClick(user)
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}