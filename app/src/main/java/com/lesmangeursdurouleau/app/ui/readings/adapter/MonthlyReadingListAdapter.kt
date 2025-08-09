// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier MonthlyReadingListAdapter.kt
package com.lesmangeursdurouleau.app.ui.readings.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.databinding.ItemMonthlyReadingBinding
import com.lesmangeursdurouleau.app.databinding.ItemPhaseDetailBinding
import java.text.SimpleDateFormat
import java.util.*

// === DÉBUT DE LA MODIFICATION ===
// JUSTIFICATION: Ces modèles de données sont créés spécifiquement pour l'UI.
// Ils contiennent les données brutes ET les informations pré-calculées par le ViewModel.
// L'Adapter n'a ainsi plus aucune logique métier à gérer.

enum class PhaseDisplayStatus {
    PLANIFIED, IN_PROGRESS, COMPLETED
}

data class PhaseUiItem(
    val name: String,
    val dateText: String,
    val iconResId: Int,
    val status: PhaseDisplayStatus,
    val meetingLink: String?
)

data class MonthlyReadingUiItem(
    val id: String,
    val book: Book?,
    val rawReading: MonthlyReading, // On garde l'objet brut pour les callbacks
    val subtitle: String,
    val customDescription: String?,
    val analysisPhase: PhaseUiItem,
    val debatePhase: PhaseUiItem,
    val isJoinable: Boolean,
    val joinableLink: String?
)
// === FIN DE LA MODIFICATION ===


class MonthlyReadingListAdapter(
    private val onLikeClicked: (MonthlyReadingUiItem) -> Unit,
    private val onCommentClicked: (MonthlyReadingUiItem) -> Unit,
    private val onJoinClicked: (meetingLink: String) -> Unit,
    private val onBookCoverClicked: (bookId: String, bookTitle: String?) -> Unit
) : ListAdapter<MonthlyReadingUiItem, MonthlyReadingListAdapter.MonthlyReadingViewHolder>(MonthlyReadingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthlyReadingViewHolder {
        val binding = ItemMonthlyReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MonthlyReadingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MonthlyReadingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MonthlyReadingViewHolder(private val binding: ItemMonthlyReadingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("StringFormatMatches")
        fun bind(item: MonthlyReadingUiItem) {
            val book = item.book
            val context = itemView.context

            binding.btnEditMonthlyReading.visibility = View.GONE
            binding.tvClubName.text = context.getString(R.string.club_name)
            binding.tvHeaderSubtitle.text = item.subtitle
            binding.tvAnimatorQuote.text = item.customDescription
            binding.tvAnimatorQuote.visibility = if (item.customDescription.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.tvBookTitle.text = book?.title ?: context.getString(R.string.unknown_book_title)
            binding.tvBookAuthor.text = book?.author ?: context.getString(R.string.unknown_author)

            binding.ivBookCover.contentDescription = context.getString(
                R.string.book_cover_of_title_description,
                book?.title ?: context.getString(R.string.unknown_book_title)
            )

            Glide.with(context)
                .load(book?.coverImageUrl)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder_error)
                .into(binding.ivBookCover)

            // JUSTIFICATION: La logique de calcul est partie. On ne fait qu'afficher les données pré-calculées.
            bindPhaseDetails(binding.phaseAnalysisLayout, item.analysisPhase)
            bindPhaseDetails(binding.phaseDebateLayout, item.debatePhase)

            binding.tvSocialFeedback.text = "❤️ 12 Appréciations"

            binding.btnJoinMeetingMain.visibility = if (item.isJoinable) View.VISIBLE else View.GONE
            binding.btnJoinMeetingMain.setOnClickListener {
                item.joinableLink?.let { onJoinClicked(it) }
            }

            binding.btnActionLike.setOnClickListener { onLikeClicked(item) }
            binding.btnActionComment.setOnClickListener { onCommentClicked(item) }

            if (book != null && book.id.isNotBlank()) {
                binding.ivBookCover.setOnClickListener { onBookCoverClicked(book.id, book.title) }
                binding.ivBookCover.isClickable = true
            } else {
                binding.ivBookCover.isClickable = false
            }
        }

        // JUSTIFICATION: Cette fonction est maintenant beaucoup plus simple. Elle ne fait aucun calcul,
        // elle se contente de mapper le `PhaseUiItem` aux vues correspondantes.
        private fun bindPhaseDetails(phaseBinding: ItemPhaseDetailBinding, phase: PhaseUiItem) {
            val context = itemView.context
            phaseBinding.ivPhaseIcon.setImageResource(phase.iconResId)
            phaseBinding.tvPhaseName.text = phase.name
            phaseBinding.tvPhaseDate.text = phase.dateText

            val chip = phaseBinding.chipPhaseStatus
            when (phase.status) {
                PhaseDisplayStatus.PLANIFIED -> {
                    chip.text = context.getString(R.string.status_planified)
                    chip.setChipBackgroundColorResource(R.color.chip_bg_planified)
                    chip.setTextColor(ContextCompat.getColor(context, R.color.chip_text_planified))
                }
                PhaseDisplayStatus.IN_PROGRESS -> {
                    chip.text = context.getString(R.string.status_in_progress)
                    chip.setChipBackgroundColorResource(R.color.chip_bg_in_progress)
                    chip.setTextColor(ContextCompat.getColor(context, R.color.chip_text_in_progress))
                }
                PhaseDisplayStatus.COMPLETED -> {
                    chip.text = context.getString(R.string.status_completed)
                    chip.setChipBackgroundColorResource(R.color.chip_bg_completed)
                    chip.setTextColor(ContextCompat.getColor(context, R.color.chip_text_completed))
                }
            }
        }
    }

    class MonthlyReadingDiffCallback : DiffUtil.ItemCallback<MonthlyReadingUiItem>() {
        override fun areItemsTheSame(oldItem: MonthlyReadingUiItem, newItem: MonthlyReadingUiItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MonthlyReadingUiItem, newItem: MonthlyReadingUiItem): Boolean {
            return oldItem == newItem
        }
    }
}