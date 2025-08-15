// CRÉEZ CE NOUVEAU FICHIER : ui/members/ForwardDestinationAdapter.kt
package com.lesmangeursdurouleau.app.ui.members

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.databinding.ItemForwardDestinationBinding

class ForwardDestinationAdapter(
    private val currentUserId: String,
    private val onConversationSelected: (Conversation) -> Unit
) : ListAdapter<Conversation, ForwardDestinationAdapter.DestinationViewHolder>(DestinationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DestinationViewHolder {
        val binding = ItemForwardDestinationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DestinationViewHolder(binding, currentUserId, onConversationSelected)
    }

    override fun onBindViewHolder(holder: DestinationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DestinationViewHolder(
        private val binding: ItemForwardDestinationBinding,
        private val currentUserId: String,
        private val onConversationSelected: (Conversation) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: Conversation) {
            val context = binding.root.context
            val otherUserId = conversation.participantIds.firstOrNull { it != currentUserId }

            if (otherUserId != null) {
                binding.tvParticipantName.text = conversation.participantNames[otherUserId] ?: context.getString(R.string.unknown_user)
                Glide.with(context)
                    .load(conversation.participantPhotoUrls[otherUserId])
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(binding.ivParticipantPhoto)
            } else {
                // Cas d'un groupe ou d'une conversation avec soi-même (si possible)
                binding.tvParticipantName.text = conversation.participantNames.values.joinToString(", ")
                binding.ivParticipantPhoto.setImageResource(R.drawable.ic_group) // Icône par défaut pour les groupes
            }

            binding.root.setOnClickListener {
                onConversationSelected(conversation)
            }
        }
    }

    class DestinationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem == newItem
        }
    }
}