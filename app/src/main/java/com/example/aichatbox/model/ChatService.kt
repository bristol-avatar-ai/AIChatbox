package com.example.aichatbox.model

class ChatService {
    var message = ""

    fun getResponse(message: String): String {
        this.message = message

        return when {
            message.contains(Regex("(?i)hi|hello")) -> useIntentGreeting()
            message.contains(Regex("(?i)name")) -> useIntentName()
            message.contains(Regex("(?i)get to|directions|go to")) -> useIntentDirections()
            else -> "Sorry, I can't help you with that."
        }
    }

    private fun useIntentGreeting(): String {
        return "Hello there!"
    }

    private fun useIntentName(): String {
        return "It's a pleasure to meet you!"
    }

    private fun useIntentDirections(): String {
        return when {
            message.contains(Regex("(?i)robot dog")) -> "Turn right at the end of the main hall. You'll find the robot dog right there!"
            message.contains(Regex("(?i)quantum computer")) -> "The quantum computer is in Room 1031, you'll find it by turning left as soon as you enter the lobby."
            message.contains(Regex("(?i)toilet|toilets")) -> "The toilets are just to the right of the main lobby area."
            else -> "Sorry, I can't help you with that."
        }
    }
}