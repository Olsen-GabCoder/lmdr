// PRÊT À COLLER - Fichier NotificationsAdapter.kt
package com.lesmangeursdurouleau.app.notifications

import android.content.Context
import android.os.Build
import android.text.Html
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Notification
import com.lesmangeursdurouleau.app.data.model.NotificationType
import com.lesmangeursdurouleau.app.databinding.ItemNotificationBinding

class NotificationsAdapter(
    private val onItemClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationsAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        private val context: Context = binding.root.context

        @RequiresApi(Build.VERSION_CODES.N)
        fun bind(notification: Notification) {
            // Clic sur l'item
            binding.notificationItemRoot.setOnClickListener {
                onItemClick(notification)
            }

            // Avatar de l'acteur
            Glide.with(context)
                .load(notification.actorProfilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(binding.ivActorAvatar)

            // Horodatage relatif (ex: "il y a 2h", "hier")
            binding.tvNotificationTimestamp.text = notification.timestamp?.let {
                DateUtils.getRelativeTimeSpanString(
                    it.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } ?: ""

            // État Lu / Non Lu
            binding.viewUnreadIndicator.isVisible = !notification.isRead
            val backgroundColor = if (notification.isRead) {
                ContextCompat.getColor(context, android.R.color.transparent)
            } else {
                ContextCompat.getColor(context, R.color.notification_unread_background) // Une couleur de fond subtile
            }
            binding.notificationItemRoot.setBackgroundColor(backgroundColor)

            // Contenu dynamique basé sur le type de notification
            val (iconRes, text) = buildContent(notification)
            binding.ivNotificationTypeIcon.setImageResource(iconRes)
            binding.tvNotificationText.text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
        }

        private fun buildContent(notification: Notification): Pair<Int, String> {
            val actor = "<b>${notification.actorUsername}</b>"
            val entity = notification.entityTitle?.let { "<b>$it</b>" }

            return when (notification.type) {
                NotificationType.NEW_FOLLOWER -> Pair(
                    R.drawable.ic_person_add,
                    context.getString(R.string.notification_new_follower, actor)
                )
                NotificationType.LIKE_ON_READING -> Pair(
                    R.drawable.ic_heart_filled,
                    context.getString(R.string.notification_like_on_reading, actor, entity ?: "votre lecture")
                )
                NotificationType.COMMENT_ON_READING -> Pair(
                    R.drawable.ic_chat_bubble,
                    context.getString(R.string.notification_comment_on_reading, actor, entity ?: "votre lecture")
                )
                // Ajoutez ici d'autres types de notifications sociales
                else -> Pair(
                    R.drawable.ic_notification_icon,
                    notification.entityTitle ?: "Vous avez une nouvelle notification."
                )
            }
        }
    }

    private class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem == newItem
        }
    }
}