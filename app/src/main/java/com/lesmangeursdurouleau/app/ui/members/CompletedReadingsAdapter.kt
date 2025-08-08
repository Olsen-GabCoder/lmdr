// PRÊT À COLLER - Remplacez tout le contenu de votre fichier CompletedReadingsAdapter.kt
package com.lesmangeursdurouleau.app.ui.members

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.ItemCompletedReadingBinding
import java.text.SimpleDateFormat
import java.util.Locale

class CompletedReadingsAdapter(
    private val currentUserId: String?,
    private val profileOwnerId: String,
    private val onItemClickListener: (CompletedReadingUiModel, ImageView) -> Unit,
    private val onDeleteClickListener: (CompletedReadingUiModel) -> Unit
) : ListAdapter<CompletedReadingUiModel, CompletedReadingsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCompletedReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCompletedReadingBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onItemClickListener(getItem(bindingAdapterPosition), binding.ivBookCover)
                }
            }
            binding.btnDeleteReading.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onDeleteClickListener(getItem(bindingAdapterPosition))
                }
            }
        }

        fun bind(item: CompletedReadingUiModel) {
            binding.btnDeleteReading.isVisible = currentUserId == profileOwnerId

            val book = item.book
            if (book != null) {
                binding.tvBookTitle.text = book.title
                binding.tvBookAuthor.text = book.author
                Glide.with(itemView.context)
                    .load(book.coverImageUrl)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder_error)
                    .into(binding.ivBookCover)
            } else {
                binding.tvBookTitle.text = itemView.context.getString(R.string.unknown_book_title)
                binding.tvBookAuthor.text = itemView.context.getString(R.string.unknown_author)
                binding.ivBookCover.setImageResource(R.drawable.ic_book_placeholder_error)
            }

            // Utilise la date de la nouvelle UserLibraryEntry
            item.libraryEntry.lastReadDate?.toDate()?.let { date ->
                val format = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
                binding.tvCompletionDate.text = itemView.context.getString(R.string.completed_on_date, format.format(date))
            } ?: run {
                binding.tvCompletionDate.text = itemView.context.getString(R.string.date_completion_unknown)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CompletedReadingUiModel>() {
        override fun areItemsTheSame(oldItem: CompletedReadingUiModel, newItem: CompletedReadingUiModel): Boolean {
            // La clé unique est l'ID du livre dans l'entrée de la bibliothèque
            return oldItem.libraryEntry.bookId == newItem.libraryEntry.bookId
        }

        override fun areContentsTheSame(oldItem: CompletedReadingUiModel, newItem: CompletedReadingUiModel): Boolean {
            return oldItem == newItem
        }
    }
}