package com.lesmangeursdurouleau.app.ui.members

import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ConversationsAdapter(
    private val currentUserId: String,
    private val onConversationClick: (conversation: Conversation) -> Unit,
    private val onFavoriteClick: (conversation: Conversation) -> Unit
) : ListAdapter<Conversation, ConversationsAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConversationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConversationViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: Conversation) {
            val otherUserId = conversation.participantIds.firstOrNull { it != currentUserId }

            if (otherUserId != null) {
                val participantName = conversation.participantNames[otherUserId] ?: "Utilisateur inconnu"
                val participantPhotoUrl = conversation.participantPhotoUrls[otherUserId]

                binding.tvParticipantName.text = participantName
                Glide.with(binding.root.context)
                    .load(participantPhotoUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(binding.ivParticipantPhoto)
            }

            binding.tvLastMessage.text = conversation.lastMessage ?: "Démarrez la conversation !"

            conversation.lastMessageTimestamp?.let {
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                binding.tvLastMessageTimestamp.text = dateFormat.format(it)
            } ?: run {
                binding.tvLastMessageTimestamp.text = ""
            }

            val unreadCount = conversation.unreadCount[currentUserId] ?: 0
            if (unreadCount > 0) {
                binding.tvUnreadCount.visibility = View.VISIBLE
                binding.tvUnreadCount.text = if (unreadCount > 9) "9+" else unreadCount.toString()
                binding.tvLastMessage.setTypeface(null, Typeface.BOLD)
                binding.tvParticipantName.setTypeface(null, Typeface.BOLD)
            } else {
                binding.tvUnreadCount.visibility = View.GONE
                binding.tvLastMessage.setTypeface(null, Typeface.NORMAL)
                binding.tvParticipantName.setTypeface(null, Typeface.NORMAL)
            }

            // --- Log de débogage pour vérifier la valeur de isFavorite ---
            Log.d("AdapterDebug", "Binding conversation. isFavorite = ${conversation.isFavorite}")

            binding.ivFavoriteIndicator.isVisible = conversation.isFavorite

            binding.root.setOnClickListener {
                onConversationClick(conversation)
            }

            binding.root.setOnLongClickListener {
                onFavoriteClick(conversation)
                true
            }
        }
    }

    class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem == newItem
        }
    }
}