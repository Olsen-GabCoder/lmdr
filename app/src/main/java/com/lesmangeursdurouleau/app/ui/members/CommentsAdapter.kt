// PRÊT À COLLER - Fichier CommentsAdapter.kt complet et MODIFIÉ
package com.lesmangeursdurouleau.app.ui.members

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

// AJOUT : Wrapper pour gérer les threads de commentaires avec DiffUtil
data class CommentThread(val parent: Comment, val replies: List<Comment>)

class CommentsAdapter(
    private val currentUserId: String?,
    private val targetProfileOwnerId: String?,
    private val lifecycleOwner: LifecycleOwner,
    // AJOUT : Nouveaux listeners pour les interactions de réponse
    private val onReplyClickListener: (parentComment: Comment) -> Unit,
    private val onViewRepliesClickListener: (parentComment: Comment) -> Unit,
    private val onDeleteClickListener: (comment: Comment) -> Unit,
    private val onLikeClickListener: (comment: Comment) -> Unit,
    private val getCommentLikeStatus: (commentId: String) -> Flow<Resource<Boolean>>
) : ListAdapter<Comment, CommentsAdapter.CommentViewHolder>(CommentDiffCallback()) {

    // AJOUT : Logique pour construire la liste affichable (avec indentation)
    private var allComments: List<Comment> = emptyList()

    fun submitCommentList(comments: List<Comment>) {
        allComments = comments
        val threadedList = buildThreadedList(comments)
        submitList(threadedList)
    }

    private fun buildThreadedList(comments: List<Comment>): List<Comment> {
        val commentMap = comments.groupBy { it.parentCommentId }
        val topLevelComments = (commentMap[null] ?: emptyList()).sortedByDescending { it.timestamp }
        val threadedList = mutableListOf<Comment>()

        topLevelComments.forEach { parent ->
            threadedList.add(parent)
            val replies = (commentMap[parent.commentId] ?: emptyList()).sortedBy { it.timestamp }
            threadedList.addAll(replies)
        }
        return threadedList
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

        fun bind(comment: Comment) {
            // Logique de base (inchangée)
            binding.tvCommentAuthorUsername.text = comment.userName.takeIf { it.isNotBlank() } ?: "Utilisateur Anonyme"
            binding.tvCommentText.text = comment.commentText
            binding.tvCommentTimestamp.text = comment.timestamp?.let { formatTimestamp(it.toDate()) }

            Glide.with(binding.root.context)
                .load(comment.userPhotoUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.ivCommentAuthorPicture)

            // Gestion du bouton Supprimer (inchangé)
            val canDelete = currentUserId != null && (comment.userId == currentUserId || currentUserId == targetProfileOwnerId)
            binding.btnDeleteComment.visibility = if (canDelete) View.VISIBLE else View.GONE
            binding.btnDeleteComment.setOnClickListener { if (canDelete) onDeleteClickListener(comment) }

            // Gestion du bouton Like (inchangé)
            binding.btnLikeComment.setOnClickListener { onLikeClickListener(comment) }
            binding.btnLikeComment.text = comment.likesCount.toString()

            likeStatusJob?.cancel()
            likeStatusJob = lifecycleOwner.lifecycleScope.launch {
                getCommentLikeStatus(comment.commentId).collectLatest { resource ->
                    val isLiked = (resource as? Resource.Success)?.data ?: false
                    updateLikeButtonState(isLiked)
                }
            }

            // AJOUT : Nouvelle logique pour les réponses
            val isReply = comment.parentCommentId != null

            // 1. Gérer l'indentation visuelle
            val constraintSet = ConstraintSet()
            constraintSet.clone(binding.root)
            val indentMargin = if (isReply) 48 else 12 // 48dp pour les réponses, 12dp pour les parents
            constraintSet.setGuidelineBegin(R.id.guideline_start_indent, indentMargin)
            constraintSet.applyTo(binding.root)

            // 2. Gérer la visibilité et les clics des boutons de réponse
            // On ne peut répondre qu'à un commentaire de premier niveau.
            if (!isReply) {
                binding.btnReplyToComment.visibility = View.VISIBLE
                binding.btnReplyToComment.setOnClickListener { onReplyClickListener(comment) }

                if (comment.replyCount > 0) {
                    binding.btnViewReplies.visibility = View.VISIBLE
                    binding.btnViewReplies.text = binding.root.context.resources.getQuantityString(R.plurals.view_replies_count, comment.replyCount, comment.replyCount)
                    binding.btnViewReplies.setOnClickListener { onViewRepliesClickListener(comment) }
                } else {
                    binding.btnViewReplies.visibility = View.GONE
                }
            } else {
                binding.btnReplyToComment.visibility = View.GONE
                binding.btnViewReplies.visibility = View.GONE
            }
        }

        private fun updateLikeButtonState(isLiked: Boolean) {
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