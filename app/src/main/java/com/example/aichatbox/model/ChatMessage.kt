package com.example.aichatbox.model

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Class to store messages in the ChatBoxViewModel.
 * The sender type and a timestamp are included.
 */
data class ChatMessage(val string: String, val sender: Int) {

    // Save timestamp on construction.
    private val timeStamp = Calendar.getInstance().time

    companion object {
        const val USER = 1
        const val AI = 2
        @SuppressLint("ConstantLocale")
        val timeStampFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    /*
    * This will return a string of the message's
    * timestamp in the format 'HH:mm'.
     */
    fun getTimeStampString(): String {
        return timeStampFormat.format(timeStamp)
    }

}