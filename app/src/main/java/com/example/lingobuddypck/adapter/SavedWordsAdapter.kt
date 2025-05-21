package com.example.lingobuddypck.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.R
import com.example.lingobuddypck.Repository.SavedWord

sealed class SavedWordListItem {
    data class Header(val letter: Char) : SavedWordListItem()
    data class WordItem(val word: SavedWord) : SavedWordListItem()
}

class SavedWordListItemDiffCallback : DiffUtil.ItemCallback<SavedWordListItem>() {
    override fun areItemsTheSame(
        oldItem: SavedWordListItem,
        newItem: SavedWordListItem
    ): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(
        oldItem: SavedWordListItem,
        newItem: SavedWordListItem
    ): Boolean {
        return oldItem == newItem
    }
}

class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val textViewHeader: TextView = itemView.findViewById(R.id.textViewHeader)
    fun bind(header: SavedWordListItem.Header) {
        textViewHeader.text = header.letter.toString()
    }
}

class SavedWordsAdapter(
    private val onEditClick: (SavedWord) -> Unit,
    private val onDeleteClick: (SavedWord) -> Unit
) : ListAdapter<SavedWordListItem, RecyclerView.ViewHolder>(SavedWordListItemDiffCallback()) {
    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SavedWordListItem.Header -> TYPE_HEADER
            is SavedWordListItem.WordItem -> TYPE_ITEM
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_saved_word_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_saved_word, parent, false)
                SavedWordViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SavedWordListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SavedWordListItem.WordItem -> (holder as SavedWordViewHolder).bind(
                item.word,
                onEditClick,
                onDeleteClick
            )
        }
    }
    class SavedWordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewWord: TextView = itemView.findViewById(R.id.textViewItemWord)
        private val textViewNote: TextView = itemView.findViewById(R.id.textViewItemNote)
        private val buttonEdit: ImageButton = itemView.findViewById(R.id.buttonEditWord)
        private val buttonDelete: ImageButton = itemView.findViewById(R.id.buttonDeleteWord)

        fun bind(
            savedWord: SavedWord,
            onEditClick: (SavedWord) -> Unit,
            onDeleteClick: (SavedWord) -> Unit
        ) {
            textViewWord.text = savedWord.word
            textViewNote.text = if (savedWord.note.isNotBlank()) savedWord.note else "No note"

            buttonEdit.setOnClickListener { onEditClick(savedWord) }
            buttonDelete.setOnClickListener { onDeleteClick(savedWord) }
        }
    }

    class SavedWordDiffCallback : DiffUtil.ItemCallback<SavedWord>() {
        override fun areItemsTheSame(oldItem: SavedWord, newItem: SavedWord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SavedWord, newItem: SavedWord): Boolean {
            return oldItem == newItem
        }
    }
}