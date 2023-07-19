package com.example.aichatbox.data

import com.example.aichatbox.model.ChatMessage

class MessageStore {
    private val messages: MutableList<ChatMessage>  = mutableListOf()

    fun addMessage(message: ChatMessage) {
        messages.add(0, message)
    }

    fun getMessage(index: Int): ChatMessage {
        return messages.get(index)
    }

    fun size(): Int {
        return messages.size
    }

    fun clear() {
        messages.clear()
    }

    override fun toString(): String {
        return "MessageStore(messages=$messages)"
    }
}