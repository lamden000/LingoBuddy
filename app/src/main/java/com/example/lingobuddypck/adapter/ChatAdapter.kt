package com.example.lingobuddypck.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lingobuddypck.Network.TogetherAI.Message
import com.example.lingobuddypck.R

class ChatAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layout = if (viewType == 0) R.layout.item_message else R.layout.item_message_other
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemViewType(position: Int): Int {
        return when (messages[position].role) {
            "user" -> 0
            else -> 1
        }
    }

    override fun getItemCount() = messages.size

    fun setMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val imageMessage: ImageView = itemView.findViewById(R.id.imageMessage)

        fun bind(message: Message) {
            if (message.imageUri != null) {
                textMessage.visibility = View.GONE
                imageMessage.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(message.imageUri)
                    .into(imageMessage)
            } else {
                imageMessage.visibility = View.GONE
                textMessage.visibility = View.VISIBLE
                textMessage.text = message.content
            }
        }
    }
}
