// PRÊT À COLLER - Créez un nouveau fichier ManageReadingsAdapter.kt dans le même package que son Fragment
package com.lesmangeursdurouleau.app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.ItemManageReadingBinding
import com.lesmangeursdurouleau.app.ui.readings.adapter.MonthlyReadingWithBook
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ManageReadingsAdapter(
    private val onReadingClicked: (MonthlyReadingWithBook) -> Unit
) : ListAdapter<MonthlyReadingWithBook, ManageReadingsAdapter.ManageReadingViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManageReadingViewHolder {
        val binding = ItemManageReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ManageReadingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ManageReadingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ManageReadingViewHolder(private val binding: ItemManageReadingBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onReadingClicked(getItem(bindingAdapterPosition))
                }
            }
        }

        fun bind(item: MonthlyReadingWithBook) {
            val context = binding.root.context
            binding.tvBookTitle.text = item.book?.title ?: context.getString(R.string.unknown_book_title)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, item.monthlyReading.year)
                set(Calendar.MONTH, item.monthlyReading.month - 1)
            }
            val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.FRENCH)
            binding.tvReadingDate.text = dateFormat.format(calendar.time).capitalize(Locale.FRENCH)

            Glide.with(context)
                .load(item.book?.coverImageUrl)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder)
                .into(binding.ivBookCover)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MonthlyReadingWithBook>() {
        override fun areItemsTheSame(oldItem: MonthlyReadingWithBook, newItem: MonthlyReadingWithBook): Boolean {
            return oldItem.monthlyReading.id == newItem.monthlyReading.id
        }

        override fun areContentsTheSame(oldItem: MonthlyReadingWithBook, newItem: MonthlyReadingWithBook): Boolean {
            return oldItem == newItem
        }
    }
}