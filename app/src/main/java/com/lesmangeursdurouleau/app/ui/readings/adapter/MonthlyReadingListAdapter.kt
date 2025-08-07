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
import com.lesmangeursdurouleau.app.data.model.Phase
import com.lesmangeursdurouleau.app.data.model.PhaseStatus
import com.lesmangeursdurouleau.app.databinding.ItemMonthlyReadingBinding
import com.lesmangeursdurouleau.app.databinding.ItemPhaseDetailBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MonthlyReadingWithBook(
    val monthlyReading: MonthlyReading,
    val book: Book?
)

enum class PhaseDisplayStatus {
    PLANIFIED, IN_PROGRESS, COMPLETED
}

class MonthlyReadingListAdapter(
    // JUSTIFICATION DE LA SUPPRESSION : La lambda `onEditClicked` est retirée du constructeur.
    // L'adaptateur n'a plus la responsabilité de gérer les actions d'administration.
    private val onLikeClicked: (MonthlyReadingWithBook) -> Unit,
    private val onCommentClicked: (MonthlyReadingWithBook) -> Unit,
    private val onJoinClicked: (meetingLink: String) -> Unit,
    private val onBookCoverClicked: (bookId: String, bookTitle: String?) -> Unit
) : ListAdapter<MonthlyReadingWithBook, MonthlyReadingListAdapter.MonthlyReadingViewHolder>(MonthlyReadingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthlyReadingViewHolder {
        val binding = ItemMonthlyReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MonthlyReadingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MonthlyReadingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MonthlyReadingViewHolder(private val binding: ItemMonthlyReadingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormatter = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
        private val subtitleDateFormatter = SimpleDateFormat("MMMM yyyy", Locale.FRENCH)

        @SuppressLint("StringFormatMatches")
        fun bind(data: MonthlyReadingWithBook) {
            val monthlyReading = data.monthlyReading
            val book = data.book
            val context = itemView.context

            // JUSTIFICATION DE LA MODIFICATION : Le bouton d'édition est maintenant toujours caché.
            // L'interface de consultation est désormais complètement séparée des actions d'administration.
            binding.btnEditMonthlyReading.visibility = View.GONE

            binding.tvClubName.text = context.getString(R.string.club_name)
            binding.tvHeaderSubtitle.text = context.getString(
                R.string.post_subtitle_template,
                subtitleDateFormatter.format(monthlyReading.getCalendar().time).capitalize(Locale.FRENCH),
            )
            binding.tvAnimatorQuote.text = monthlyReading.customDescription?.let { "\"$it\"" } ?: ""
            binding.tvAnimatorQuote.visibility = if (monthlyReading.customDescription.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.tvBookTitle.text = book?.title ?: context.getString(R.string.unknown_book_title)
            binding.tvBookAuthor.text = book?.author ?: context.getString(R.string.unknown_author)

            binding.ivBookCover.contentDescription = context.getString(
                R.string.book_cover_of_title_description,
                book?.title ?: context.getString(R.string.unknown_book_title)
            )

            if (!book?.coverImageUrl.isNullOrEmpty()) {
                if (book != null) {
                    Glide.with(context)
                        .load(book.coverImageUrl)
                        .placeholder(R.drawable.ic_book_placeholder)
                        .error(R.drawable.ic_book_placeholder_error)
                        .into(binding.ivBookCover)
                }
            } else {
                binding.ivBookCover.setImageResource(R.drawable.ic_book_placeholder)
            }

            val analysisStatus = bindPhaseDetails(
                binding.phaseAnalysisLayout,
                monthlyReading.analysisPhase,
                context.getString(R.string.analysis_phase_label),
                R.drawable.ic_analysis
            )

            val debateStatus = bindPhaseDetails(
                binding.phaseDebateLayout,
                monthlyReading.debatePhase,
                context.getString(R.string.debate_phase_label),
                R.drawable.ic_people
            )

            binding.tvSocialFeedback.text = "❤️ 12 Appréciations"

            val isAnalysisJoinable = analysisStatus == PhaseDisplayStatus.IN_PROGRESS && !monthlyReading.analysisPhase.meetingLink.isNullOrBlank()
            val isDebateJoinable = debateStatus == PhaseDisplayStatus.IN_PROGRESS && !monthlyReading.debatePhase.meetingLink.isNullOrBlank()

            binding.btnJoinMeetingMain.visibility = if (isAnalysisJoinable || isDebateJoinable) View.VISIBLE else View.GONE

            // JUSTIFICATION DE LA SUPPRESSION : Le listener pour le bouton d'édition est retiré.
            // binding.btnEditMonthlyReading.setOnClickListener { onEditClicked(data) }
            binding.btnActionLike.setOnClickListener { onLikeClicked(data) }
            binding.btnActionComment.setOnClickListener { onCommentClicked(data) }

            binding.btnJoinMeetingMain.setOnClickListener {
                val linkToOpen = if (isAnalysisJoinable) monthlyReading.analysisPhase.meetingLink else monthlyReading.debatePhase.meetingLink
                linkToOpen?.let { onJoinClicked(it) }
            }

            if (book != null && book.id.isNotBlank()) {
                binding.ivBookCover.setOnClickListener { onBookCoverClicked(book.id, book.title) }
                binding.ivBookCover.isClickable = true
                binding.ivBookCover.isFocusable = true
            } else {
                binding.ivBookCover.setOnClickListener(null)
                binding.ivBookCover.isClickable = false
                binding.ivBookCover.isFocusable = false
            }
        }

        private fun bindPhaseDetails(
            phaseBinding: ItemPhaseDetailBinding,
            phase: Phase,
            phaseName: String,
            iconResId: Int
        ): PhaseDisplayStatus {
            val context = itemView.context
            phaseBinding.ivPhaseIcon.setImageResource(iconResId)
            phaseBinding.tvPhaseName.text = phaseName
            phaseBinding.tvPhaseDate.text = phase.date?.let { dateFormatter.format(it) } ?: "Date non définie"

            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time

            val phaseDate = phase.date?.let {
                Calendar.getInstance().apply { time = it; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
            }

            val displayStatus = when {
                phase.status == PhaseStatus.COMPLETED -> PhaseDisplayStatus.COMPLETED
                phase.status == PhaseStatus.IN_PROGRESS -> PhaseDisplayStatus.IN_PROGRESS
                phaseDate == null -> PhaseDisplayStatus.PLANIFIED
                phaseDate.before(today) -> PhaseDisplayStatus.COMPLETED
                phaseDate == today -> PhaseDisplayStatus.IN_PROGRESS
                else -> PhaseDisplayStatus.PLANIFIED
            }

            val chip = phaseBinding.chipPhaseStatus
            when (displayStatus) {
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
            return displayStatus
        }
    }

    class MonthlyReadingDiffCallback : DiffUtil.ItemCallback<MonthlyReadingWithBook>() {
        override fun areItemsTheSame(oldItem: MonthlyReadingWithBook, newItem: MonthlyReadingWithBook): Boolean {
            return oldItem.monthlyReading.id == newItem.monthlyReading.id
        }

        override fun areContentsTheSame(oldItem: MonthlyReadingWithBook, newItem: MonthlyReadingWithBook): Boolean {
            return oldItem == newItem
        }
    }

    private fun MonthlyReading.getCalendar(): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
        }
    }
}