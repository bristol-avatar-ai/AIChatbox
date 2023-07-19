package com.example.aichatbox.model

data class ChatMessage(val string: String, val sender: Int) {
    override fun toString(): String {
        return "ChatMessage(string='$string', sender=$sender)"
    }
}