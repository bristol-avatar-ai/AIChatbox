package com.example.aichatbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.example.aichatbox.adapter.MessageAdapter
import com.example.aichatbox.data.ChatBoxViewModel
import com.example.aichatbox.databinding.FragmentChatBoxBinding
import com.example.aichatbox.model.ChatMessage
import com.example.aichatbox.model.ChatService

/**
 * Fragment containing chat interface.
 */
class ChatBoxFragment : Fragment() {

    // Attach chat history ViewModel. Delegate to viewModels to retain its value through configuration changes.
    private val viewModel: ChatBoxViewModel by viewModels()
    // Initialize ChatService for message responses.
    private val chatService = ChatService()

    private var _binding: FragmentChatBoxBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentChatBoxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = binding.chatHistory
        recyclerView.adapter = MessageAdapter(viewModel)
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
            R.id.action_reset_chat -> resetChatBox()
            else -> super.onOptionsItemSelected(item)
        }
    }

    /*
    * Resets the chat history and ChatService instance.
    * Clears the message input and notifies the RecyclerView to rebind the items.
     */
    private fun resetChatBox() : Boolean {
        val itemCount = viewModel.getChatHistorySize()
        viewModel.clearChatHistory()
        chatService.reset()
        binding.messageInput.text?.clear()
        binding.chatHistory.adapter?.notifyItemRangeRemoved(0, itemCount)
        return true
    }

    private fun sendMessage() {
        val message = binding.messageInput.text.toString()
        val adapter = recyclerView.adapter

        if(message.isNotEmpty()) {
            // Press a button, record a message
            // Convert voice to text

            viewModel.addMessage(ChatMessage(message, ChatMessage.USER))
            binding.messageInput.text?.clear()
            adapter?.notifyItemInserted(0)
            recyclerView.scrollToPosition(0)

            viewModel.addMessage(ChatMessage(chatService.getResponse(message), ChatMessage.AI))
            adapter?.notifyItemInserted(0)
            recyclerView.scrollToPosition(0)

            // Enter some code that converts the response to audio.
        }
    }



}