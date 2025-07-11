// PRÊT À COLLER - Fichier CommentsAdapter.kt complet et MODIFIÉ
package com.lesmangeursdurouleau.app.ui.members

import android.content.res.ColorStateList
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
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

// AJOUT : Interface pour découpler les actions de l'adapter.
interface OnCommentInteractionListener {
    fun onMentionClicked(username: String)
    fun onHashtagClicked(hashtag: String)
}

class CommentsAdapter(
    private val currentUserId: String?,
    private val lifecycleOwner: LifecycleOwner,
    // AJOUT : Le listener pour les interactions de texte est maintenant requis.
    private val interactionListener: OnCommentInteractionListener,
    private val onReplyClickListener: (parentComment: Comment) -> Unit,
    private val onCommentOptionsClickListener: (comment: Comment, anchorView: View) -> Unit,
    private val onLikeClickListener: (comment: Comment) -> Unit,
    private val getCommentLikeStatus: (commentId: String) -> Flow<Resource<Boolean>>
) : ListAdapter<Comment, CommentsAdapter.CommentViewHolder>(CommentDiffCallback()) {

    private val expandedCommentIds = mutableSetOf<String>()
    private var allComments: List<Comment> = emptyList()
    private val likedCommentsOptimisticState = mutableMapOf<String, Boolean>()


    fun submitCommentList(comments: List<Comment>) {
        allComments = comments
        likedCommentsOptimisticState.clear()
        updateDisplayedList()
    }

    private fun updateDisplayedList() {
        val commentMap = allComments.groupBy { it.parentCommentId }
        val topLevelComments = (commentMap[null] ?: emptyList()).sortedByDescending { it.timestamp }
        val displayedList = mutableListOf<Comment>()

        topLevelComments.forEach { parent ->
            displayedList.add(parent)
            if (expandedCommentIds.contains(parent.commentId)) {
                val replies = (commentMap[parent.commentId] ?: emptyList()).sortedBy { it.timestamp }
                displayedList.addAll(replies)
            }
        }
        submitList(displayedList)
    }

    fun toggleRepliesVisibility(commentId: String) {
        if (expandedCommentIds.contains(commentId)) {
            expandedCommentIds.remove(commentId)
        } else {
            expandedCommentIds.add(commentId)
        }
        updateDisplayedList()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(private val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        private var likeStatusJob: Job? = null

        // AJOUT : Définition des patterns Regex pour la réutilisation.
        private val mentionPattern = Pattern.compile("@(\\w+)")
        private val hashtagPattern = Pattern.compile("#(\\w+)")

        fun bind(comment: Comment) {
            binding.tvCommentAuthorUsername.text = comment.userName.takeIf { it.isNotBlank() } ?: "Utilisateur Anonyme"

            // MODIFICATION : Utilisation d'une fonction pour parser et afficher le texte du commentaire.
            renderCommentText(comment.commentText)

            binding.tvCommentTimestamp.text = comment.timestamp?.let { formatTimestamp(it.toDate()) }

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
                val currentState = likedCommentsOptimisticState[comment.commentId]
                    ?: (getCommentLikeStatus(comment.commentId) as? Resource.Success<Boolean>)?.data ?: false
                val newState = !currentState
                likedCommentsOptimisticState[comment.commentId] = newState
                updateLikeButtonState(newState, comment.likesCount, true)
                it.startAnimation(AnimationUtils.loadAnimation(it.context, R.anim.like_button_animation))
                onLikeClickListener(comment)
            }

            binding.btnLikeComment.text = comment.likesCount.toString()

            likeStatusJob?.cancel()
            likeStatusJob = lifecycleOwner.lifecycleScope.launch {
                getCommentLikeStatus(comment.commentId).collectLatest { resource ->
                    val isLiked = (resource as? Resource.Success)?.data ?: false
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

        private fun updateLikeButtonState(isLiked: Boolean, currentLikesCount: Int, isOptimisticUpdate: Boolean = false) {
            val iconRes = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            binding.btnLikeComment.setIconResource(iconRes)

            val colorAttr = if (isLiked) R.color.red_love else com.google.android.material.R.attr.colorOnSurfaceVariant
            val resolvedColor = if (isLiked) {
                ContextCompat.getColor(binding.root.context, colorAttr)
            } else {
                MaterialColors.getColor(binding.btnLikeComment, colorAttr)
            }
            binding.btnLikeComment.iconTint = ColorStateList.valueOf(resolvedColor)
            binding.btnLikeComment.setTextColor(resolvedColor)

            if (isOptimisticUpdate) {
                val currentCount = binding.btnLikeComment.text.toString().toIntOrNull() ?: currentLikesCount
                binding.btnLikeComment.text = if(isLiked) {
                    (currentCount + 1).toString()
                } else {
                    (currentCount - 1).coerceAtLeast(0).toString()
                }
            } else {
                binding.btnLikeComment.text = currentLikesCount.toString()
            }
        }

        // AJOUT : Fonction pour parser le texte, appliquer les spans et rendre le texte cliquable.
        private fun renderCommentText(text: String) {
            val spannableString = SpannableString(text)
            val primaryColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)

            // Traitement des mentions
            val mentionMatcher = mentionPattern.matcher(text)
            while (mentionMatcher.find()) {
                val mention = mentionMatcher.group(1) // Capture le texte SANS le '@'
                if (mention != null) {
                    val clickableSpan = object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            interactionListener.onMentionClicked(mention)
                        }
                    }
                    spannableString.setSpan(clickableSpan, mentionMatcher.start(), mentionMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), mentionMatcher.start(), mentionMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(ForegroundColorSpan(primaryColor), mentionMatcher.start(), mentionMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            // Traitement des hashtags
            val hashtagMatcher = hashtagPattern.matcher(text)
            while (hashtagMatcher.find()) {
                val hashtag = hashtagMatcher.group(1) // Capture le texte SANS le '#'
                if (hashtag != null) {
                    val clickableSpan = object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            interactionListener.onHashtagClicked(hashtag)
                        }
                    }
                    spannableString.setSpan(clickableSpan, hashtagMatcher.start(), hashtagMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), hashtagMatcher.start(), hashtagMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(ForegroundColorSpan(primaryColor), hashtagMatcher.start(), hashtagMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            binding.tvCommentText.text = spannableString
            binding.tvCommentText.movementMethod = LinkMovementMethod.getInstance() // Essentiel pour que les clics fonctionnent
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

    private class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean = oldItem.commentId == newItem.commentId
        override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean = oldItem == newItem
    }
}