package com.lesmangeursdurouleau.app.ui.members

import androidx.recyclerview.widget.DiffUtil
import com.lesmangeursdurouleau.app.data.model.EnrichedUserListItem

class EnrichedUserListItemDiffCallback : DiffUtil.ItemCallback<EnrichedUserListItem>() {
    override fun areItemsTheSame(oldItem: EnrichedUserListItem, newItem: EnrichedUserListItem): Boolean {
        return oldItem.uid == newItem.uid
    }

    override fun areContentsTheSame(oldItem: EnrichedUserListItem, newItem: EnrichedUserListItem): Boolean {
        return oldItem == newItem
    }
}