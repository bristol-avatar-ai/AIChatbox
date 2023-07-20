package com.example.aichatbox

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aichatbox.adapter.MessageAdapter
import com.example.aichatbox.data.MessageStore
import com.example.aichatbox.databinding.FragmentChatBoxBinding
import com.example.aichatbox.model.ChatMessage
import com.example.aichatbox.model.ChatService

class ChatBoxFragment : Fragment() {

    private var _binding: FragmentChatBoxBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView

    // Initialize data.
    private val messageStore = MessageStore()
    private val chatService = ChatService()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentChatBoxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = binding.chatHistory
        recyclerView.adapter = MessageAdapter(messageStore)
        recyclerView.setHasFixedSize(true)

        binding.sendButton.setOnClickListener {
            sendMessage()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.reset_chat_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset_chat -> {
                messageStore.clear()
                binding.messageInput.text?.clear()
                binding.chatHistory.adapter?.notifyDataSetChanged()
                chatService.reset()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun sendMessage() {
        val message = binding.messageInput.text.toString()
        val adapter = recyclerView.adapter

        if(message.isNotEmpty()) {
            // Press a button, record a message
            // Convert voice to text

            messageStore.addMessage(ChatMessage(message, ChatMessage.USER))
            binding.messageInput.text?.clear()
            adapter?.notifyItemInserted(0)
            recyclerView.scrollToPosition(0)

            messageStore.addMessage(ChatMessage(chatService.getResponse(message), ChatMessage.AI))
            adapter?.notifyItemInserted(0)
            recyclerView.scrollToPosition(0)

            // Enter some code that converts the response to audio.
        }
    }
}