// PRÊT À COLLER - Créez un nouveau fichier MyLibraryAdapter.kt
package com.lesmangeursdurouleau.app.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.ReadingStatus
import com.lesmangeursdurouleau.app.databinding.ItemMyLibraryBookBinding

class MyLibraryAdapter(
    // Lambdas pour gérer les interactions de l'utilisateur
    private val onBookClicked: (LibraryBookItem) -> Unit,
    private val onFavoriteClicked: (LibraryBookItem) -> Unit,
    private val onMarkAsReadClicked: (LibraryBookItem) -> Unit
) : ListAdapter<LibraryBookItem, MyLibraryAdapter.LibraryBookViewHolder>(LibraryBookDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryBookViewHolder {
        val binding = ItemMyLibraryBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LibraryBookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LibraryBookViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LibraryBookViewHolder(private val binding: ItemMyLibraryBookBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            // Clic sur l'ensemble de l'item
            binding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onBookClicked(getItem(bindingAdapterPosition))
                }
            }
            // Clics sur les boutons spécifiques
            binding.btnFavorite.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onFavoriteClicked(getItem(bindingAdapterPosition))
                }
            }
            binding.btnMarkAsRead.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onMarkAsReadClicked(getItem(bindingAdapterPosition))
                }
            }
        }

        fun bind(item: LibraryBookItem) {
            val context = binding.root.context
            val book = item.book
            val entry = item.entry

            // Affichage des informations du livre
            binding.tvBookTitle.text = book?.title ?: context.getString(R.string.unknown_book_title)
            binding.tvBookAuthor.text = book?.author ?: context.getString(R.string.unknown_author)

            Glide.with(context)
                .load(book?.coverImageUrl)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder_error)
                .into(binding.ivBookCover)

            binding.ivBookCover.contentDescription = context.getString(
                R.string.book_cover_of_title_description,
                book?.title ?: context.getString(R.string.unknown_book_title)
            )

            // Logique de la barre de progression
            if (entry.totalPages > 0 && entry.status == ReadingStatus.READING) {
                binding.progressBar.visibility = ViewGroup.VISIBLE
                binding.progressBar.max = entry.totalPages
                binding.progressBar.progress = entry.currentPage
            } else {
                binding.progressBar.visibility = ViewGroup.GONE
            }

            // TODO: La logique visuelle des boutons (ex: étoile pleine/vide) sera ajoutée dans une prochaine étape.
        }
    }

    class LibraryBookDiffCallback : DiffUtil.ItemCallback<LibraryBookItem>() {
        override fun areItemsTheSame(oldItem: LibraryBookItem, newItem: LibraryBookItem): Boolean {
            return oldItem.entry.bookId == newItem.entry.bookId
        }

        override fun areContentsTheSame(oldItem: LibraryBookItem, newItem: LibraryBookItem): Boolean {
            return oldItem == newItem
        }
    }
}