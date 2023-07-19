package com.example.aichatbox

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.aichatbox.adapter.MessageAdapter
import com.example.aichatbox.data.MessageStore
import com.example.aichatbox.databinding.ActivityMainBinding
import com.example.aichatbox.model.ChatMessage
import com.example.aichatbox.model.ChatService
import com.example.aichatbox.const.Sender

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    // Initialize data.
    private val messageStore = MessageStore()
    private val chatService = ChatService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recyclerView = binding.chatHistory
        recyclerView.adapter = MessageAdapter(this, messageStore)
        recyclerView.setHasFixedSize(true)

        binding.sendButton.setOnClickListener() { sendMessage(recyclerView) }
    }

    private fun sendMessage(recyclerView: RecyclerView) {
        val message = binding.messageInput.text.toString()
        val adapter = recyclerView.adapter

        if(message.isNotEmpty()) {
            // Press a button, record a message
            // Convert voice to text

            messageStore.addMessage(ChatMessage(message, Sender.USER))
            binding.messageInput.text?.clear()
            adapter?.notifyItemInserted(0)
            recyclerView.scrollToPosition(0)

            messageStore.addMessage(ChatMessage(chatService.getResponse(message), Sender.AI))
            adapter?.notifyItemInserted(0)
            recyclerView.scrollToPosition(0)

            // Enter some code that converts the response to audio.
        }
    }
}