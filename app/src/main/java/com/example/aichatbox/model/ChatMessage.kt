package com.example.aichatbox.model

data class ChatMessage(val string: String, val sender: Int) {

    companion object {
        const val USER = 1
        const val AI = 2
    }

}