package com.example.lingobuddypck.adapter

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.AnimatedVectorDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.Log
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

class ChatAdapter(
    private val messages: MutableList<Message>,
    private val context: Context, // Giữ lại theo code gốc của bạn
    private val firebaseWordRepository: FirebaseWordRepository, // Giữ lại theo code gốc của bạn
    private val onSpeakClick: (String?) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AI = 1
        private const val VIEW_TYPE_TYPING_INDICATOR = 2 // Kiểu view mới
    }

    override fun getItemViewType(position: Int): Int {
        if (position < 0 || position >= messages.size) {
            return VIEW_TYPE_AI
        }
        return when (messages[position].role) {
            "user" -> VIEW_TYPE_USER
            "assistant" -> VIEW_TYPE_AI
            "typing_indicator" -> VIEW_TYPE_TYPING_INDICATOR
            else -> VIEW_TYPE_AI // Mặc định hoặc xử lý lỗi nếu cần
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_message, parent, false)
                UserMessageViewHolder(view)
            }
            VIEW_TYPE_AI -> {
                val view = inflater.inflate(R.layout.item_message_other, parent, false)
                // AIMessageViewHolder là inner class nên có thể truy cập context, firebaseWordRepository từ ChatAdapter
                // nếu cần, nhưng tốt hơn là truyền qua constructor như bạn đang làm.
                AIMessageViewHolder(view, context, firebaseWordRepository, onSpeakClick)
            }
            VIEW_TYPE_TYPING_INDICATOR -> {
                val view = inflater.inflate(R.layout.item_typing_indicator, parent, false)
                TypingIndicatorViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Kiểm tra an toàn cho messages[position]
        if (position < 0 || position >= messages.size) return

        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AIMessageViewHolder -> holder.bind(message)
            is TypingIndicatorViewHolder -> holder.bind()
        }
    }

    override fun getItemCount(): Int = messages.size

    fun setMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged() // Cân nhắc dùng DiffUtil để hiệu năng tốt hơn với danh sách lớn
    }

    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val messageImage: ImageView = itemView.findViewById(R.id.messageImage) // Giữ lại theo code gốc

        fun bind(message: Message) {

            textMessage.text = message.content

            // Logic hiển thị hình ảnh của bạn (giữ nguyên)
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
        val regex = Regex("<en>(.*?)</en>",RegexOption.DOT_MATCHES_ALL)

        regex.findAll(input).forEach { matchResult ->
            val start = matchResult.range.first
            if (start > currentIndex) {
                spannable.append(input.substring(currentIndex, start))
            }

            val englishText = matchResult.groupValues[1] // Chỉ lấy nội dung bên trong tag

            // Ghi nhớ vị trí bắt đầu đoạn tiếng Anh để tô màu/đậm
            val spanStart = spannable.length
            spannable.append(englishText) // Nối đoạn tiếng Anh (đã bỏ tag)
            val spanEnd = spannable.length

            // Tô đậm hoặc đổi màu đoạn tiếng Anh
            spannable.setSpan(
                StyleSpan(Typeface.BOLD), // Ví dụ: tô đậm
                spanStart,
                spanEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            currentIndex = matchResult.range.last + 1 // Cập nhật currentIndex sau </en>
        }

        // Thêm phần còn lại của chuỗi sau thẻ <en> cuối cùng (nếu có)
        if (currentIndex < input.length) {
            spannable.append(input.substring(currentIndex))
        }
        // Nếu không tìm thấy tag nào, trả về chuỗi gốc
        return if (spannable.isEmpty() && currentIndex == 0) SpannableStringBuilder(input) else spannable
    }

    inner class AIMessageViewHolder(
        itemView: View,
        private val context: Context, // context này từ ChatAdapter
        private val firebaseWordRepository: FirebaseWordRepository, // repo này từ ChatAdapter
        private val onSpeakClick: (String?) -> Unit // callback này từ ChatAdapter
    ) : RecyclerView.ViewHolder(itemView) {

        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val speakBtn: ImageButton = itemView.findViewById(R.id.speakButton)
        // private val avatarImage: ImageView = itemView.findViewById(R.id.avatarAI) // Avatar được xử lý trong layout item_message_other

        fun bind(message: Message) {
            message.content?.let { Log.d("DEBUGCHAT", it) }
            val displayText = formatTextWithHighlightedEnglish(message.content)
            textMessage.text = displayText ?: message.content // Fallback nếu format lỗi

            speakBtn.setOnClickListener {
                onSpeakClick(message.content) // TTS vẫn dùng content gốc có tag
            }

            // Logic save word của bạn (giữ nguyên)
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

    class TypingIndicatorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typingIndicatorImageView: ImageView = itemView.findViewById(R.id.typingIndicatorImageView)
        private var avd: AnimatedVectorDrawable? = null

        init {
            val drawable = typingIndicatorImageView.drawable
            if (drawable is AnimatedVectorDrawable) {
                avd = drawable
            }
        }

        fun bind() {
            // AVD với repeatCount="infinite" thường tự chạy khi visible.
            // Gọi start() để đảm bảo.
            avd?.start()
        }

        fun onAttached() {
            avd?.start()
        }

        fun onDetached() {
            avd?.stop()
        }
    }

    // Override các hàm này trong Adapter để quản lý animation của TypingIndicatorViewHolder
    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is TypingIndicatorViewHolder) {
            holder.onAttached()
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is TypingIndicatorViewHolder) {
            holder.onDetached()
        }
    }
}