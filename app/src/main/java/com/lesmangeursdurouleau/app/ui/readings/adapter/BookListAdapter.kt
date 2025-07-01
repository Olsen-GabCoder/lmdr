// PRÊT À COLLER - Remplacez tout le contenu de votre fichier BookListAdapter.kt par ceci.
package com.lesmangeursdurouleau.app.ui.readings.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.databinding.ItemBookBinding

class BookListAdapter(private val onItemClicked: (Book) -> Unit) :
    ListAdapter<Book, BookListAdapter.BookViewHolder>(BookDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = getItem(position)
        holder.bind(book)
        holder.itemView.setOnClickListener {
            onItemClicked(book)
        }
    }

    inner class BookViewHolder(private val binding: ItemBookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(book: Book) {
            val context = itemView.context
            binding.tvBookTitle.text = book.title
            binding.tvBookAuthor.text = book.author
            binding.tvBookSynopsisPreview.text = book.synopsis ?: context.getString(R.string.no_synopsis_available)

            // === DÉBUT DE LA MODIFICATION ===
            // Définition de la contentDescription dynamique pour l'accessibilité
            binding.ivBookCoverPlaceholder.contentDescription = context.getString(
                R.string.book_cover_of_title_description,
                book.title.ifBlank { context.getString(R.string.unknown_book_title) }
            )
            // === FIN DE LA MODIFICATION ===

            if (!book.coverImageUrl.isNullOrEmpty()) {
                Glide.with(context)
                    .load(book.coverImageUrl)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder_error)
                    .into(binding.ivBookCoverPlaceholder)
            } else {
                binding.ivBookCoverPlaceholder.setImageResource(R.drawable.ic_book_placeholder)
            }
        }
    }

    class BookDiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem == newItem
        }
    }
}