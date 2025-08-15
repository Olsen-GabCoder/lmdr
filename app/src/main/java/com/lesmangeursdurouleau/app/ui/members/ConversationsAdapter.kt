// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ConversationsAdapter.kt
package com.lesmangeursdurouleau.app.ui.members

import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.data.model.MessageStatus
import com.lesmangeursdurouleau.app.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

interface ConversationAdapterListener {
    fun onConversationClicked(conversation: Conversation)
    fun onConversationLongClicked(conversation: Conversation)
    fun onConversationSelected(conversation: Conversation, isSelected: Boolean)
}

class ConversationsAdapter(
    private val currentUserId: String,
    private val listener: ConversationAdapterListener
) : ListAdapter<Conversation, ConversationsAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    private val selectedItems = mutableSetOf<String>()
    private var isSelectionMode = false

    fun isInSelectionMode(): Boolean = isSelectionMode

    fun getSelectedConversations(): List<Conversation> {
        return currentList.filter { selectedItems.contains(it.id) }
    }

    fun toggleSelection(conversationId: String) {
        if (selectedItems.contains(conversationId)) {
            selectedItems.remove(conversationId)
        } else {
            selectedItems.add(conversationId)
        }
        notifyItemChanged(currentList.indexOfFirst { it.id == conversationId })
    }

    fun startSelectionMode(conversation: Conversation) {
        isSelectionMode = true
        selectedItems.add(conversation.id!!)
        notifyItemChanged(currentList.indexOf(conversation))
    }

    fun clearSelection() {
        isSelectionMode = false
        val previouslySelected = selectedItems.toList()
        selectedItems.clear()
        previouslySelected.forEach { id ->
            val index = currentList.indexOfFirst { it.id == id }
            if (index != -1) notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConversationViewHolder(binding, currentUserId, listener, this)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val isSelected = selectedItems.contains(getItem(position).id)
        holder.bind(getItem(position), isSelected)
    }

    class ConversationViewHolder(
        private val binding: ItemConversationBinding,
        private val currentUserId: String,
        private val listener: ConversationAdapterListener,
        private val adapter: ConversationsAdapter
    ) : RecyclerView.ViewHolder(binding.root) {

        private val context = binding.root.context
        private val selectedOverlay = ColorDrawable(ContextCompat.getColor(context, R.color.selected_item_overlay))

        fun bind(conversation: Conversation, isSelected: Boolean) {
            binding.root.setOnClickListener {
                if (adapter.isInSelectionMode()) {
                    adapter.toggleSelection(conversation.id!!)
                    listener.onConversationSelected(conversation, !isSelected)
                } else {
                    listener.onConversationClicked(conversation)
                }
            }
            binding.root.setOnLongClickListener {
                if (!adapter.isInSelectionMode()) {
                    listener.onConversationLongClicked(conversation)
                }
                true
            }

            if (isSelected) {
                binding.root.foreground = selectedOverlay
                binding.selectionIcon.isVisible = true
            } else {
                binding.root.foreground = null
                binding.selectionIcon.isVisible = false
            }

            val otherUserId = conversation.participantIds.firstOrNull { it != currentUserId }

            if (otherUserId != null) {
                binding.tvParticipantName.text = conversation.participantNames[otherUserId] ?: context.getString(R.string.unknown_user)
                Glide.with(context)
                    .load(conversation.participantPhotoUrls[otherUserId])
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(binding.ivParticipantPhoto)
            }

            val unreadCount = conversation.unreadCount[currentUserId] ?: 0
            val isUnread = unreadCount > 0
            binding.tvUnreadCount.isVisible = isUnread
            if (isUnread) {
                binding.tvUnreadCount.text = unreadCount.toString()
                binding.tvParticipantName.setTypeface(null, Typeface.BOLD)
                binding.tvLastMessage.setTypeface(null, Typeface.BOLD)
                binding.tvLastMessageTimestamp.setTextColor(ContextCompat.getColor(context, R.color.primary_accent))
            } else {
                binding.tvParticipantName.setTypeface(null, Typeface.NORMAL)
                binding.tvLastMessage.setTypeface(null, Typeface.NORMAL)
                binding.tvLastMessageTimestamp.setTextColor(ContextCompat.getColor(context, R.color.text_color_secondary))
            }

            binding.tvLastMessageTimestamp.text = formatTimestamp(conversation.lastMessageTimestamp)
            binding.ivPinnedIndicator.isVisible = conversation.isPinned

            val isLastMessageFromMe = conversation.lastMessageSenderId == currentUserId
            binding.ivReadStatus.isVisible = isLastMessageFromMe && !isUnread

            if (isLastMessageFromMe) {
                binding.tvLastMessage.text = context.getString(R.string.you_preview, conversation.lastMessage)
                // === DÉBUT DE LA CORRECTION : Logique de statut de lecture ===
                when (conversation.lastMessageStatus) {
                    MessageStatus.READ.name -> {
                        binding.ivReadStatus.setImageResource(R.drawable.ic_check_double)
                        binding.ivReadStatus.setColorFilter(ContextCompat.getColor(context, R.color.status_read_color))
                    }
                    MessageStatus.SENT.name -> {
                        binding.ivReadStatus.setImageResource(R.drawable.ic_check_single)
                        binding.ivReadStatus.setColorFilter(ContextCompat.getColor(context, R.color.text_color_secondary))
                    }
                    else -> {
                        binding.ivReadStatus.isVisible = false
                    }
                }
                // === FIN DE LA CORRECTION ===
            } else {
                binding.tvLastMessage.text = conversation.lastMessage ?: context.getString(R.string.start_conversation)
            }
        }

        private fun formatTimestamp(date: Date?): String {
            if (date == null) return ""
            val calendar = Calendar.getInstance()
            val today = Calendar.getInstance()
            calendar.time = date
            val diff = today.timeInMillis - calendar.timeInMillis
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            return when {
                days == 0L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                days == 1L -> context.getString(R.string.yesterday)
                else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
            }
        }
    }

    class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean = oldItem == newItem
    }
}