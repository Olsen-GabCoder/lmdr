// Fichier complet : CommentsAdapter.kt

package com.lesmangeursdurouleau.app.ui.members

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.databinding.ItemCommentBinding
import com.lesmangeursdurouleau.app.databinding.ItemCommentHiddenBinding
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

interface OnCommentInteractionListener {
    fun onMentionClicked(username: String)
    fun onHashtagClicked(hashtag: String)
}

class CommentsAdapter(
    private val currentUserId: String?,
    private val isOwnerOfReading: Boolean,
    private val lifecycleOwner: LifecycleOwner,
    private val interactionListener: OnCommentInteractionListener,
    private val onReplyClickListener: (parentComment: Comment) -> Unit,
    private val onCommentOptionsClickListener: (comment: Comment, anchorView: View) -> Unit,
    private val onLikeClickListener: (comment: Comment) -> Unit,
    private val onUnhideClickListener: (commentId: String) -> Unit,
    private val getCommentLikeStatus: (commentId: String) -> Flow<Resource<Boolean>>
) : ListAdapter<UiComment, RecyclerView.ViewHolder>(UiCommentDiffCallback()) {

    var highlightedCommentId: String? = null

    private companion object {
        const val VIEW_TYPE_NORMAL = 1
        const val VIEW_TYPE_HIDDEN = 2
    }

    private val expandedCommentIds = mutableSetOf<String>()
    private var allParentComments: List<UiComment> = emptyList()
    private var allRepliesMap: Map<String, List<UiComment>> = emptyMap()
    private val likedCommentsOptimisticState = mutableMapOf<String, Boolean>()

    fun submitNewComments(parents: List<UiComment>, replies: Map<String, List<UiComment>>) {
        allParentComments = parents
        allRepliesMap = replies
        updateDisplayedList()
    }

    fun clearAllComments() {
        allParentComments = emptyList()
        allRepliesMap = emptyMap()
        expandedCommentIds.clear()
        likedCommentsOptimisticState.clear()
        updateDisplayedList()
    }

    private fun updateDisplayedList() {
        val displayedList = mutableListOf<UiComment>()
        allParentComments.forEach { parentUiComment ->
            displayedList.add(parentUiComment)
            if (expandedCommentIds.contains(parentUiComment.comment.commentId)) {
                val replies = allRepliesMap[parentUiComment.comment.commentId] ?: emptyList()
                displayedList.addAll(replies)
            }
        }
        submitList(displayedList)
    }

    private fun toggleRepliesVisibility(commentId: String) {
        if (expandedCommentIds.contains(commentId)) {
            expandedCommentIds.remove(commentId)
        } else {
            expandedCommentIds.add(commentId)
        }
        updateDisplayedList()
    }

    // JUSTIFICATION DE LA MODIFICATION : Correction de l'erreur de typographie critique.
    // Le nom de la méthode a été changé de `getItemViewtype` à `getItemViewType`.
    // L'annotation `@Override` a été ajoutée. Cette correction assure que notre logique est bien
    // utilisée par le RecyclerView pour déterminer le type de vue, ce qui résout
    // l'exception `IllegalArgumentException` et le crash de l'application.
    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isHidden) {
            VIEW_TYPE_HIDDEN
        } else {
            VIEW_TYPE_NORMAL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_NORMAL -> {
                val binding = ItemCommentBinding.inflate(inflater, parent, false)
                CommentViewHolder(binding)
            }
            VIEW_TYPE_HIDDEN -> {
                val binding = ItemCommentHiddenBinding.inflate(inflater, parent, false)
                HiddenCommentViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val uiComment = getItem(position)
        when (holder) {
            is CommentViewHolder -> holder.bind(uiComment.comment)
            is HiddenCommentViewHolder -> holder.bind(uiComment)
        }
    }

    inner class HiddenCommentViewHolder(private val binding: ItemCommentHiddenBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(uiComment: UiComment) {
            binding.unhideCommentButton.isVisible = isOwnerOfReading
            if (isOwnerOfReading) {
                binding.unhideCommentButton.setOnClickListener {
                    onUnhideClickListener(uiComment.comment.commentId)
                }
            } else {
                binding.unhideCommentButton.setOnClickListener(null)
            }
        }
    }

    inner class CommentViewHolder(private val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        private var likeStatusJob: Job? = null
        private val mentionPattern = Pattern.compile("@(\\w+)")
        private val hashtagPattern = Pattern.compile("#(\\w+)")

        fun bind(comment: Comment) {
            if (comment.commentId == highlightedCommentId) {
                highlightAndFade()
                highlightedCommentId = null
            } else {
                binding.root.setBackgroundColor(Color.TRANSPARENT)
            }

            binding.tvCommentAuthorUsername.text = comment.userName.takeIf { it.isNotBlank() } ?: "Utilisateur Anonyme"
            renderCommentText(comment.commentText)

            val timestampText = comment.timestamp?.let { formatTimestamp(it.toDate()) }
            val editedIndicator = if (comment.isEdited) " (modifié)" else ""
            binding.tvCommentTimestamp.text = "$timestampText$editedIndicator"

            Glide.with(binding.root.context)
                .load(comment.userPhotoUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.ivCommentAuthorPicture)

            binding.btnCommentOptions.setOnClickListener {
                onCommentOptionsClickListener(comment, it)
            }

            binding.btnLikeComment.setOnClickListener {
                val currentState = likedCommentsOptimisticState[comment.commentId] ?: (binding.btnLikeComment.tag as? Boolean ?: false)
                val newState = !currentState
                likedCommentsOptimisticState[comment.commentId] = newState
                updateLikeButtonState(newState, comment.likesCount, true)
                it.startAnimation(AnimationUtils.loadAnimation(it.context, R.anim.like_button_animation))
                onLikeClickListener(comment)
            }

            likeStatusJob?.cancel()
            likeStatusJob = lifecycleOwner.lifecycleScope.launch {
                getCommentLikeStatus(comment.commentId).collectLatest { resource ->
                    val isLiked = (resource as? Resource.Success)?.data ?: false
                    binding.btnLikeComment.tag = isLiked
                    val displayState = likedCommentsOptimisticState[comment.commentId] ?: isLiked
                    updateLikeButtonState(displayState, comment.likesCount)
                }
            }

            val isReply = comment.parentCommentId != null
            val constraintSet = ConstraintSet()
            constraintSet.clone(binding.root)
            val indentMarginDp = if (isReply) 48 else 12
            val indentMarginPx = (indentMarginDp * binding.root.context.resources.displayMetrics.density).toInt()
            constraintSet.setGuidelineBegin(R.id.guideline_start_indent, indentMarginPx)
            constraintSet.applyTo(binding.root)

            if (!isReply) {
                binding.btnReplyToComment.visibility = View.VISIBLE
                binding.btnReplyToComment.setOnClickListener { onReplyClickListener(comment) }

                if (comment.replyCount > 0) {
                    binding.btnViewReplies.visibility = View.VISIBLE
                    val isExpanded = expandedCommentIds.contains(comment.commentId)
                    if (isExpanded) {
                        binding.btnViewReplies.text = binding.root.context.getString(R.string.action_hide_replies)
                        binding.btnViewReplies.setIconResource(R.drawable.ic_arrow_up)
                    } else {
                        binding.btnViewReplies.text = binding.root.context.resources.getQuantityString(R.plurals.view_replies_count, comment.replyCount, comment.replyCount)
                        binding.btnViewReplies.setIconResource(R.drawable.ic_arrow_down)
                    }
                    binding.btnViewReplies.setOnClickListener {
                        toggleRepliesVisibility(comment.commentId)
                    }
                } else {
                    binding.btnViewReplies.visibility = View.GONE
                }
            } else {
                binding.btnReplyToComment.visibility = View.GONE
                binding.btnViewReplies.visibility = View.GONE
            }
        }

        private fun highlightAndFade() {
            val colorFrom = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimaryContainer)
            val colorTo = Color.TRANSPARENT
            val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
            colorAnimation.duration = 1500
            colorAnimation.startDelay = 500
            colorAnimation.addUpdateListener { animator ->
                binding.root.setBackgroundColor(animator.animatedValue as Int)
            }
            colorAnimation.start()
        }

        private fun updateLikeButtonState(isLiked: Boolean, currentLikesCount: Int, isOptimisticUpdate: Boolean = false) {
            val iconRes = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            binding.btnLikeComment.setIconResource(iconRes)

            val colorAttr = if (isLiked) R.color.red_love else com.google.android.material.R.attr.colorOnSurfaceVariant
            val resolvedColor = if (isLiked) ContextCompat.getColor(binding.root.context, colorAttr)
            else MaterialColors.getColor(binding.btnLikeComment, colorAttr)
            binding.btnLikeComment.iconTint = ColorStateList.valueOf(resolvedColor)
            binding.btnLikeComment.setTextColor(resolvedColor)

            val realCount = currentLikesCount
            if (isOptimisticUpdate) {
                val optimisticCount = if(isLiked) realCount + 1 else realCount - 1
                binding.btnLikeComment.text = optimisticCount.coerceAtLeast(0).toString()
            } else {
                binding.btnLikeComment.text = realCount.toString()
            }
        }

        private fun renderCommentText(text: String) {
            val spannableString = SpannableString(text)
            val primaryColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)

            val mentionMatcher = mentionPattern.matcher(text)
            while (mentionMatcher.find()) {
                val mention = mentionMatcher.group(1)
                if (mention != null) {
                    val clickableSpan = object : ClickableSpan() {
                        override fun onClick(widget: View) { interactionListener.onMentionClicked(mention) }
                    }
                    spannableString.setSpan(clickableSpan, mentionMatcher.start(), mentionMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), mentionMatcher.start(), mentionMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(ForegroundColorSpan(primaryColor), mentionMatcher.start(), mentionMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            val hashtagMatcher = hashtagPattern.matcher(text)
            while (hashtagMatcher.find()) {
                val hashtag = hashtagMatcher.group(1)
                if (hashtag != null) {
                    val clickableSpan = object : ClickableSpan() {
                        override fun onClick(widget: View) { interactionListener.onHashtagClicked(hashtag) }
                    }
                    spannableString.setSpan(clickableSpan, hashtagMatcher.start(), hashtagMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), hashtagMatcher.start(), hashtagMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(ForegroundColorSpan(primaryColor), hashtagMatcher.start(), hashtagMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            binding.tvCommentText.text = spannableString
            binding.tvCommentText.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun formatTimestamp(date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "maintenant"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
        }
    }

    private class UiCommentDiffCallback : DiffUtil.ItemCallback<UiComment>() {
        override fun areItemsTheSame(oldItem: UiComment, newItem: UiComment): Boolean {
            return oldItem.comment.commentId == newItem.comment.commentId
        }
        override fun areContentsTheSame(oldItem: UiComment, newItem: UiComment): Boolean {
            return oldItem == newItem
        }
    }
}