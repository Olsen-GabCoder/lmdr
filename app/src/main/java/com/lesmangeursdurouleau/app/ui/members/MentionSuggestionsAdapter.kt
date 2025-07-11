// PRÊT À COLLER - Fichier MentionSuggestionsAdapter.kt complet (NOUVEAU FICHIER)
package com.lesmangeursdurouleau.app.ui.members

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.ItemMentionSuggestionBinding

class MentionSuggestionsAdapter(
    private val onUserClickListener: (User) -> Unit
) : ListAdapter<User, MentionSuggestionsAdapter.MentionViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MentionViewHolder {
        val binding = ItemMentionSuggestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MentionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MentionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MentionViewHolder(private val binding: ItemMentionSuggestionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onUserClickListener(getItem(adapterPosition))
                }
            }
        }

        fun bind(user: User) {
            binding.tvMentionUsername.text = user.username
            Glide.with(binding.root.context)
                .load(user.profilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.ivMentionAvatar)
        }
    }

    private class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}