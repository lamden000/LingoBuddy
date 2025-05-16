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
import com.example.lingobuddypck.ViewModel.Repository.SavedWord

class SavedWordsAdapter(
    private val onEditClick: (SavedWord) -> Unit,
    private val onDeleteClick: (SavedWord) -> Unit
) : ListAdapter<SavedWord, SavedWordsAdapter.SavedWordViewHolder>(SavedWordDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedWordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_word, parent, false) // Sử dụng layout item đã tạo
        return SavedWordViewHolder(view)
    }

    override fun onBindViewHolder(holder: SavedWordViewHolder, position: Int) {
        val word = getItem(position)
        holder.bind(word, onEditClick, onDeleteClick)
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