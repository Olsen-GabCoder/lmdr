package com.lesmangeursdurouleau.app.ui.members

import android.icu.text.SimpleDateFormat
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.*
import com.lesmangeursdurouleau.app.databinding.ItemDateSeparatorBinding
import com.lesmangeursdurouleau.app.databinding.ItemLoadingIndicatorBinding
import com.lesmangeursdurouleau.app.databinding.ItemPrivateMessageReceivedBinding
import com.lesmangeursdurouleau.app.databinding.ItemPrivateMessageSentBinding
import java.util.*

private const val VIEW_TYPE_SENT = 1
private const val VIEW_TYPE_RECEIVED = 2
private const val VIEW_TYPE_DATE_SEPARATOR = 3
private const val VIEW_TYPE_LOADING = 4

private object AnimateReactionPayload
private object UserInfoUpdatePayload

class PrivateMessagesAdapter(
    private val currentUserId: String,
    private val onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit,
    private val onImageClick: (imageUrl: String) -> Unit,
    private val formatDateLabel: (date: Date) -> String,
    val onMessageSwiped: (message: PrivateMessage) -> Unit,
    private val onReplyClicked: (messageId: String) -> Unit
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    private var currentUser: User? = null
    private var targetUser: User? = null

    fun setCurrentUser(user: User?) {
        if (this.currentUser != user) {
            this.currentUser = user
            updateVisibleItems(VIEW_TYPE_SENT, UserInfoUpdatePayload)
        }
    }

    fun setTargetUser(user: User?) {
        if (this.targetUser != user) {
            this.targetUser = user
            updateVisibleItems(VIEW_TYPE_RECEIVED, UserInfoUpdatePayload)
        }
    }

    private fun updateVisibleItems(viewTypeToUpdate: Int, payload: Any) {
        for (i in 0 until itemCount) {
            if (getItemViewType(i) == viewTypeToUpdate) {
                notifyItemChanged(i, payload)
            }
        }
    }

    fun animateReactionForMessage(messageId: String) {
        val position = currentList.indexOfFirst { it is MessageItem && it.message.id == messageId }
        if (position != -1) {
            notifyItemChanged(position, AnimateReactionPayload)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is MessageItem -> if (item.message.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
            is DateSeparatorItem -> VIEW_TYPE_DATE_SEPARATOR
            is LoadingIndicatorItem -> VIEW_TYPE_LOADING
            else -> throw IllegalArgumentException("ViewType invalide pour ChatItem à la position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> SentMessageViewHolder(ItemPrivateMessageSentBinding.inflate(inflater, parent, false), onReplyClicked)
            VIEW_TYPE_RECEIVED -> ReceivedMessageViewHolder(ItemPrivateMessageReceivedBinding.inflate(inflater, parent, false), onReplyClicked)
            VIEW_TYPE_DATE_SEPARATOR -> DateSeparatorViewHolder(ItemDateSeparatorBinding.inflate(inflater, parent, false))
            VIEW_TYPE_LOADING -> LoadingViewHolder(ItemLoadingIndicatorBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("ViewType invalide : $viewType")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SentMessageViewHolder -> holder.bind(currentUser, (getItem(position) as MessageItem).message, onMessageLongClick, onImageClick)
            is ReceivedMessageViewHolder -> holder.bind(targetUser, (getItem(position) as MessageItem).message, onMessageLongClick, onImageClick)
            is DateSeparatorViewHolder -> holder.bind(getItem(position) as DateSeparatorItem, formatDateLabel)
            is LoadingViewHolder -> { /* Rien à binder */ }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        payloads.forEach { payload ->
            when (payload) {
                is UserInfoUpdatePayload -> {
                    if (holder is SentMessageViewHolder) holder.bindAvatar(currentUser)
                    if (holder is ReceivedMessageViewHolder) holder.bindAvatar(targetUser)
                }
                is AnimateReactionPayload -> {
                    if (holder is BaseMessageViewHolder) holder.playReactionAnimation()
                }
            }
        }
    }

    class LoadingViewHolder(binding: ItemLoadingIndicatorBinding) : RecyclerView.ViewHolder(binding.root)

    class DateSeparatorViewHolder(private val binding: ItemDateSeparatorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DateSeparatorItem, formatDateLabel: (date: Date) -> String) {
            binding.tvDateSeparator.text = formatDateLabel(item.timestamp)
        }
    }

    abstract class BaseMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        protected abstract val reactionsContainer: LinearLayout

        fun playReactionAnimation() {
            val animation = AnimationUtils.loadAnimation(itemView.context, R.anim.reaction_pop)
            reactionsContainer.startAnimation(animation)
        }

        protected fun bindReactions(reactions: Map<String, String>) {
            reactionsContainer.isVisible = reactions.isNotEmpty()
            if (reactions.isNotEmpty()) {
                reactionsContainer.removeAllViews()
                val reactionCounts = reactions.values.groupingBy { it }.eachCount()
                reactionCounts.forEach { (emoji, count) ->
                    val reactionTextView = TextView(itemView.context).apply {
                        text = if (count > 1) "$emoji $count" else emoji
                        textSize = 14f
                        background = ContextCompat.getDrawable(context, R.drawable.bg_reactions)
                        setPadding(16, 8, 16, 8)
                        val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        layoutParams.marginEnd = 8
                        this.layoutParams = layoutParams
                    }
                    reactionsContainer.addView(reactionTextView)
                }
            }
        }
    }

    class SentMessageViewHolder(
        private val binding: ItemPrivateMessageSentBinding,
        private val onReplyClicked: (messageId: String) -> Unit
    ) : BaseMessageViewHolder(binding.root) {
        override val reactionsContainer: LinearLayout = binding.llReactionsContainer

        fun bindAvatar(user: User?) {
            Glide.with(itemView.context)
                .load(user?.profilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(binding.ivSenderAvatar)
        }

        @RequiresApi(Build.VERSION_CODES.N)
        fun bind(
            currentUser: User?,
            message: PrivateMessage,
            onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit,
            onImageClick: (imageUrl: String) -> Unit
        ) {
            bindAvatar(currentUser)

            // === DÉBUT DE LA MODIFICATION ===
            binding.forwardedIndicatorContainer.isVisible = message.isForwarded
            // === FIN DE LA MODIFICATION ===

            binding.replyContainer.isVisible = message.replyInfo != null
            message.replyInfo?.let { replyInfo ->
                binding.tvReplySenderName.text = if (replyInfo.repliedToSenderName == "Vous") "Vous" else replyInfo.repliedToSenderName
                binding.tvReplyPreview.text = replyInfo.repliedToMessagePreview
                binding.replyContainer.setOnClickListener { onReplyClicked(replyInfo.repliedToMessageId) }
            }
            binding.tvMessageBody.isVisible = !message.text.isNullOrBlank()
            binding.tvMessageBody.text = message.text
            binding.ivMessageImage.isVisible = message.imageUrl != null
            message.imageUrl?.let { imageUrl ->
                Glide.with(itemView.context).load(imageUrl).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_error).into(binding.ivMessageImage)
                binding.ivMessageImage.setOnClickListener { onImageClick(imageUrl) }
            }
            binding.tvMessageTimestamp.text = message.timestamp?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: ""
            bindReactions(message.reactions)
            binding.tvEditedIndicator.isVisible = message.isEdited
            val statusIcon = binding.ivMessageStatus
            statusIcon.isVisible = message.senderId == currentUser?.uid
            when (message.status) {
                MessageStatus.READ.name -> {
                    statusIcon.setImageResource(R.drawable.ic_check_double); statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.status_read_color))
                }
                MessageStatus.SENT.name -> {
                    statusIcon.setImageResource(R.drawable.ic_check_single); statusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.white_60_alpha))
                }
                else -> statusIcon.isVisible = false
            }
            itemView.setOnLongClickListener { onMessageLongClick(binding.bubbleContainer, message); true }
        }
    }

    class ReceivedMessageViewHolder(
        private val binding: ItemPrivateMessageReceivedBinding,
        private val onReplyClicked: (messageId: String) -> Unit
    ) : BaseMessageViewHolder(binding.root) {
        override val reactionsContainer: LinearLayout = binding.llReactionsContainer

        fun bindAvatar(user: User?) {
            Glide.with(itemView.context)
                .load(user?.profilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(binding.ivSenderAvatar)
        }

        @RequiresApi(Build.VERSION_CODES.N)
        fun bind(
            targetUser: User?,
            message: PrivateMessage,
            onMessageLongClick: (anchorView: View, message: PrivateMessage) -> Unit,
            onImageClick: (imageUrl: String) -> Unit
        ) {
            bindAvatar(targetUser)

            // === DÉBUT DE LA MODIFICATION ===
            binding.forwardedIndicatorContainer.isVisible = message.isForwarded
            // === FIN DE LA MODIFICATION ===

            binding.replyContainer.isVisible = message.replyInfo != null
            message.replyInfo?.let { replyInfo ->
                binding.tvReplySenderName.text = if (replyInfo.repliedToSenderName == "Vous") "Vous" else replyInfo.repliedToSenderName
                binding.tvReplyPreview.text = replyInfo.repliedToMessagePreview
                binding.replyContainer.setOnClickListener { onReplyClicked(replyInfo.repliedToMessageId) }
            }
            binding.tvMessageBody.isVisible = !message.text.isNullOrBlank()
            binding.tvMessageBody.text = message.text
            binding.ivMessageImage.isVisible = message.imageUrl != null
            message.imageUrl?.let { imageUrl ->
                Glide.with(itemView.context).load(imageUrl).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_error).into(binding.ivMessageImage)
                binding.ivMessageImage.setOnClickListener { onImageClick(imageUrl) }
            }
            binding.tvMessageTimestamp.text = message.timestamp?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: ""
            bindReactions(message.reactions)
            binding.tvEditedIndicator.isVisible = message.isEdited
            itemView.setOnLongClickListener { onMessageLongClick(binding.bubbleContainer, message); true }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
        override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean = oldItem == newItem
    }
}