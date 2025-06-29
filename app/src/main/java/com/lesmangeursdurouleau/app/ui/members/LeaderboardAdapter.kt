// PRÊT À COLLER - Fichier complet
package com.lesmangeursdurouleau.app.ui.members // ou com.lesmangeursdurouleau.app.ui.leaderboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.databinding.ItemLeaderboardEntryBinding

class LeaderboardAdapter : ListAdapter<Conversation, LeaderboardAdapter.LeaderboardViewHolder>(LeaderboardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val binding = ItemLeaderboardEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LeaderboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bind(conversation, position + 1) // On passe la position pour le classement (1, 2, 3...)
    }

    class LeaderboardViewHolder(private val binding: ItemLeaderboardEntryBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: Conversation, position: Int) {
            val context = binding.root.context

            // Afficher la position
            binding.tvPosition.text = position.toString()

            // Logique pour afficher la médaille pour le top 3
            when (position) {
                1 -> {
                    binding.tvPosition.text = "🏆"
                    binding.tvPosition.background.setTint(ContextCompat.getColor(context, R.color.gold_medal))
                }
                2 -> {
                    binding.tvPosition.text = "🥈"
                    binding.tvPosition.background.setTint(ContextCompat.getColor(context, R.color.silver_medal))
                }
                3 -> {
                    binding.tvPosition.text = "🥉"
                    binding.tvPosition.background.setTint(ContextCompat.getColor(context, R.color.bronze_medal))
                }
                else -> {
                    // Pour les autres, on remet la couleur par défaut
                    binding.tvPosition.background.setTint(ContextCompat.getColor(context, R.color.primary_accent))
                }
            }

            // Afficher les noms des participants
            val names = conversation.participantNames.values.joinToString(" & ")
            binding.tvParticipantNames.text = names

            // Afficher le score
            binding.tvScore.text = conversation.affinityScore.toString()

            // Afficher les photos de profil
            val photoUrls = conversation.participantPhotoUrls.values.toList()
            Glide.with(context)
                .load(photoUrls.getOrNull(0))
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(binding.ivParticipant1)

            Glide.with(context)
                .load(photoUrls.getOrNull(1))
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(binding.ivParticipant2)
        }
    }

    class LeaderboardDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            // On ne compare que le score pour rafraîchir la vue si le score change
            return oldItem.affinityScore == newItem.affinityScore
        }
    }
}