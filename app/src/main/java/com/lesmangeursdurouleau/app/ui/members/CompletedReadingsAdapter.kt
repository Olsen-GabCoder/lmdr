// PRÊT À COLLER - Nouveau Fichier
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
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.CompletedReading
import com.lesmangeursdurouleau.app.databinding.ItemCompletedReadingBinding
import java.text.SimpleDateFormat
import java.util.Locale

// NOUVELLE data class pour la couche UI, combinant les données.
data class CompletedReadingWithBook(
    val completedReading: CompletedReading,
    val book: Book? // Peut être null si le livre a été supprimé
)

class CompletedReadingsAdapter(
    private val currentUserId: String?,
    private val profileOwnerId: String,
    private val onItemClickListener: (CompletedReadingWithBook, ImageView) -> Unit,
    private val onDeleteClickListener: (CompletedReadingWithBook) -> Unit
) : ListAdapter<CompletedReadingWithBook, CompletedReadingsAdapter.ViewHolder>(DiffCallback()) {

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

        fun bind(item: CompletedReadingWithBook) {
            binding.btnDeleteReading.isVisible = currentUserId == profileOwnerId

            val book = item.book
            if (book != null) {
                // Si le livre existe, on affiche ses informations
                binding.tvBookTitle.text = book.title
                binding.tvBookAuthor.text = book.author
                Glide.with(itemView.context)
                    .load(book.coverImageUrl)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder_error)
                    .into(binding.ivBookCover)
            } else {
                // Si le livre a été supprimé, on affiche un état de fallback
                binding.tvBookTitle.text = itemView.context.getString(R.string.unknown_book_title)
                binding.tvBookAuthor.text = itemView.context.getString(R.string.unknown_author)
                binding.ivBookCover.setImageResource(R.drawable.ic_book_placeholder_error)
            }

            item.completedReading.completionDate?.let { date ->
                val format = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
                binding.tvCompletionDate.text = itemView.context.getString(R.string.completed_on_date, format.format(date))
            } ?: run {
                binding.tvCompletionDate.text = itemView.context.getString(R.string.date_completion_unknown)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CompletedReadingWithBook>() {
        override fun areItemsTheSame(oldItem: CompletedReadingWithBook, newItem: CompletedReadingWithBook): Boolean {
            // La clé unique est l'ID du livre dans le contexte de l'historique d'un utilisateur.
            return oldItem.completedReading.bookId == newItem.completedReading.bookId
        }

        override fun areContentsTheSame(oldItem: CompletedReadingWithBook, newItem: CompletedReadingWithBook): Boolean {
            // On compare l'objet complet. Si le livre ou la date changent, l'item est redessiné.
            return oldItem == newItem
        }
    }
}