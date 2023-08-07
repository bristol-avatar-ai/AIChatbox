package com.example.aichatbox.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.aichatbox.model.ChatMessage

/**
 * ViewModel containing the chat history and methods to modify it.
 */
class ChatBoxViewModel : ViewModel() {

    // Store message history as MutableLiveData backing property.
    private val _messages = MutableLiveData<MutableList<ChatMessage>>()
    // messages is public read-only.
    val messages : MutableLiveData<MutableList<ChatMessage>>
        get() = _messages

    /*
    * Initialise mutable list of chat _messages.
     */
    init {
        _messages.value = mutableListOf()
    }

    /*
    * Adds a new message to the chat history.
    * Newest _messages are stored first.
    */
    fun addMessage(message: ChatMessage) {
        _messages.value?.add(0, message)
    }

    /*
    * Gets a message at the requested index.
    * Used in the chat_history RecyclerView.
    */
    fun getMessage(index: Int): ChatMessage {
        return _messages.value!![index]
    }

    /*
    * Get the number of _messages stored.
    */
    fun getChatHistorySize(): Int {
        return _messages.value?.size ?: 0
    }

    /*
    * Clear the message history.
    */
    fun clearChatHistory() {
        _messages.value?.clear()
    }

}