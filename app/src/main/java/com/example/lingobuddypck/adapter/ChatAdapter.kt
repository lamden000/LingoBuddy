package com.example.lingobuddypck.adapter

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.Services.Message
import com.example.lingobuddypck.R
import com.example.lingobuddypck.Repository.FirebaseWordRepository
import com.example.lingobuddypck.utils.enableSelectableSaveAction

class ChatAdapter(private val messages: MutableList<Message>, private val context: Context,
                  private val firebaseWordRepository: FirebaseWordRepository,
                  private val onSpeakClick: (String?) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AI = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].role == "user") VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val view = inflater.inflate(R.layout.item_message, parent, false)
            UserMessageViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_message_other, parent, false)
            AIMessageViewHolder(view, context, firebaseWordRepository, onSpeakClick) // 👈 Truyền callback vào đây
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AIMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun setMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val messageImage: ImageView = itemView.findViewById(R.id.messageImage)

        fun bind(message: Message) {
            textMessage.text = message.content
            if (message.imageUri != null) {
                messageImage.visibility = View.VISIBLE
                messageImage.setImageURI(message.imageUri)
            } else {
                messageImage.visibility = View.GONE
            }
        }
    }

    fun formatTextWithHighlightedEnglish(input: String?): SpannableStringBuilder? {
        if (input == null) return null

        val spannable = SpannableStringBuilder()
        var currentIndex = 0
        val regex = Regex("<en>(.*?)</en>")

        regex.findAll(input).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            val before = input.substring(currentIndex, start)
            val englishText = matchResult.groupValues[1]

            // Thêm phần trước đoạn <en>
            spannable.append(before)

            // Ghi nhớ vị trí bắt đầu đoạn tiếng Anh
            val spanStart = spannable.length
            spannable.append(englishText)
            val spanEnd = spannable.length

            // Tô đậm hoặc đổi màu đoạn tiếng Anh
            spannable.setSpan(
                StyleSpan(Typeface.BOLD), // hoặc ForegroundColorSpan(Color.BLUE)
                spanStart,
                spanEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            currentIndex = end
        }

        // Thêm phần còn lại sau thẻ cuối
        if (currentIndex < input.length) {
            spannable.append(input.substring(currentIndex))
        }

        return spannable
    }

    inner class AIMessageViewHolder(
        itemView: View,
        private val context: Context,
        private val firebaseWordRepository: FirebaseWordRepository,
        private val onSpeakClick: (String?) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val speakBtn: ImageButton = itemView.findViewById(R.id.speakButton)

        fun bind(message: Message) {
            val rawText = message.content
            val displayText = formatTextWithHighlightedEnglish(rawText)

            textMessage.text = displayText
            speakBtn.setOnClickListener {
                onSpeakClick(message.content)
            }

            textMessage.enableSelectableSaveAction(context) { selectedText, note ->
                firebaseWordRepository.saveWord(
                    word = selectedText,
                    note = note,
                    onSuccess = {
                        Toast.makeText(context, "Đã lưu \"$selectedText\"!", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                        Toast.makeText(context, "Lỗi khi lưu từ: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
