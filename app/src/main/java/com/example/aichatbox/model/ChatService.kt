package com.example.aichatbox.model

class ChatService {
    
    companion object {
        const val NONE : Int = 1
        const val LOCATION : Int = 2
        const val INFORMATION : Int = 3
    }

    private lateinit var inputMessage: String
    
    // Backing properties: public read-only.
    // User request type.
    private var _userRequest : Int = NONE
    val userRequest: Int
        get() = _userRequest
    // Destination string, used for LOCATION requests.
    private var _destination : String? = null
    val destination: String?
        get() = _destination

    fun getResponse(message: String): String {
        inputMessage = message

        return when {
            inputMessage.contains(Regex("(?i)hi|hello")) -> useIntentGreeting()
            inputMessage.contains(Regex("(?i)name")) -> useIntentName()
            inputMessage.contains(Regex("(?i)get to|directions|go to")) -> useIntentDirections()
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
            inputMessage.contains(Regex("(?i)robot dog")) -> "Turn right at the end of the main hall. You'll find the robot dog right there!"
            inputMessage.contains(Regex("(?i)quantum computer")) -> "The quantum computer is in Room 1031, you'll find it by turning left as soon as you enter the lobby."
            inputMessage.contains(Regex("(?i)toilet|toilets")) -> "The toilets are just to the right of the main lobby area."
            else -> "Sorry, I can't help you with that."
        }
    }

    fun reset() {
        // Nothing to reset yet.
    }
}