package com.example.lingobuddypck.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.Factory.RolePlayViewModelFactory
import com.example.lingobuddypck.R
import com.example.lingobuddypck.ViewModel.RolePlayChatViewModel
import com.example.lingobuddypck.adapter.ChatAdapter
import com.example.lingobuddypck.data.ChatItemDecoration

class RolePlayChatFragment : Fragment() {

    private lateinit var viewModel: RolePlayChatViewModel
    private lateinit var adapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_chat_with_ai, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val role = arguments?.getString("AIRole") ?: "Giáo viên"
        val contextText = arguments?.getString("context") ?: "Lớp học tiếng Anh"

        val factory = RolePlayViewModelFactory(role, contextText)
        viewModel = ViewModelProvider(this, factory)[RolePlayChatViewModel::class.java]

        val inputMessage = view.findViewById<EditText>(R.id.inputMessage)
        val sendButton = view.findViewById<Button>(R.id.sendButton)
        val loadingSpinner = view.findViewById<ProgressBar>(R.id.loadingSpinner)
        val recyclerView = view.findViewById<RecyclerView>(R.id.chatRecyclerView)

        adapter = ChatAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(ChatItemDecoration(50))

        viewModel.chatMessages.observe(viewLifecycleOwner) { messages ->
            adapter.setMessages(messages)
            recyclerView.scrollToPosition(messages.size - 1)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingSpinner.visibility = if (isLoading) View.VISIBLE else View.GONE
            sendButton.isEnabled = !isLoading
        }

        sendButton.setOnClickListener {
            val message = inputMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                viewModel.sendMessage(message)
                inputMessage.text.clear()
            } else {
                Toast.makeText(requireContext(), "Vui lòng nhập tin nhắn", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
