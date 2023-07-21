package com.example.aichatbox.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aichatbox.R
import com.example.aichatbox.data.ChatBoxViewModel
import com.example.aichatbox.model.ChatMessage

/**
 * Adaptor for the chatHistory RecycleView.
 * Comments have not been included as this class
 * will likely not be used in production.
 */
class MessageAdapter(
    private val viewModel: ChatBoxViewModel
    ): RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(private val view: View, viewType: Int) : RecyclerView.ViewHolder(view) {
        val textView: TextView = when(viewType) {
            ChatMessage.USER -> view.findViewById(R.id.user_message_title)
            else -> view.findViewById(R.id.ai_message_title)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return viewModel.getMessage(position).sender
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val adapterLayout = when(viewType) {
            ChatMessage.USER -> LayoutInflater.from(parent.context)
                .inflate(R.layout.user_message, parent, false)
            else -> LayoutInflater.from(parent.context)
                .inflate(R.layout.ai_message, parent, false)
        }

        return MessageViewHolder(adapterLayout, viewType)
    }

    override fun getItemCount() = viewModel.getChatHistorySize()

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = viewModel.getMessage(position)
        holder.textView.text = message.string
    }

}