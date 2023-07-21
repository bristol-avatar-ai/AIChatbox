package com.example.aichatbox.data

import androidx.lifecycle.ViewModel
import com.example.aichatbox.model.ChatMessage

/**
 * ViewModel containing the chat history and methods to modify it.
 */
class ChatBoxViewModel : ViewModel() {

    // Mutable list of chat messages.
    private val messages: MutableList<ChatMessage> = mutableListOf()

    /*
    * Adds a new message to the chat history.
    * Newest messages are stored first.
    */
    fun addMessage(message: ChatMessage) {
        messages.add(0, message)
    }

    /*
    * Gets a message at the requested index.
    * Used in the chat_history RecyclerView.
    */
    fun getMessage(index: Int): ChatMessage {
        return messages[index]
    }

    /*
    * Get the number of messages stored.
    */
    fun getChatHistorySize(): Int {
        return messages.size
    }

    /*
    * Clear the message history.
    */
    fun clearChatHistory() {
        messages.clear()
    }

}