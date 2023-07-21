package com.example.aichatbox

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.example.aichatbox.adapter.MessageAdapter
import com.example.aichatbox.data.ChatBoxViewModel
import com.example.aichatbox.databinding.FragmentChatBoxBinding
import com.example.aichatbox.model.ChatMessage
import com.example.aichatbox.model.ChatService
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

const val TAG = "ChatBoxFragment"

/**
 * Fragment containing chat interface and TextToSpeech services.
 */
class ChatBoxFragment : Fragment(), OnInitListener {

    // Attach chat history ViewModel. Delegate to viewModels to retain its value through configuration changes.
    private val viewModel: ChatBoxViewModel by viewModels()
    // Initialise ChatService for message responses.
    private val chatService = ChatService()
    // Set to true to enable user chat input.
    private var chatBoxReady = false
    // TextToSpeech class for audio responses.
    private lateinit var textToSpeech: TextToSpeech
    // Set to true when TextToSpeech service is ready.
    private var textToSpeechReady = false

    private var _binding: FragmentChatBoxBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate fragment.
        _binding = FragmentChatBoxBinding.inflate(inflater, container, false)
        // Enable options menu.
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = binding.chatHistory
        // Create and assign adaptor to RecyclerView.
        adapter = MessageAdapter(viewModel)
        recyclerView.adapter = this.adapter
        // Improves RecyclerView performance, remove if RecyclerView dimensions can change.
        recyclerView.setHasFixedSize(true)

        // Initialise TextToSpeech
        textToSpeech = TextToSpeech(requireContext(), this)

        // Observe the messages LiveData, passing in the LifecycleOwner and the observer.
        viewModel.messages.observe(viewLifecycleOwner) {
            // Optional: Migrate adaptor updates here.
        }

        // Sends message when the send button is clicked.
        binding.sendButton.setOnClickListener {
            if(chatBoxReady) { inputReceived() }
        }

        setExitMessageEditListener() // See below.
    }

    /*
    * This hides the keyboard and stops editing messageInput when
    * the chatHistory RecyclerView is touched.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setExitMessageEditListener() {
        recyclerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val manager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                        as InputMethodManager
                manager.hideSoftInputFromWindow(binding.messageInput.windowToken, 0)
                binding.messageInput.clearFocus()
            }
            false
        }
    }

    /*
    * onInit is called when the TextToSpeech service is initialised.
    * User input is disabled by default and enabled here by setting
    * chatBoxReady to true.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set the language of TextToSpeech.
            val result = textToSpeech.setLanguage(Locale.UK)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Log error if failed.
                Log.e(TAG, "Failed to set TextToSpeech language: '$result'.")
            } else {
                textToSpeechReady = true
            }
        } else {
            // Log error if failed.
            Log.e(TAG, "TextToSpeech failed to initialise.")
        }
        chatBoxReady = true
        // Disable visibility of the loading animation after initialisation.
        binding.loadingAnimation.visibility = View.INVISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatBoxReady = false
        textToSpeech.stop()
        textToSpeech.shutdown()
        textToSpeechReady = false
        _binding = null
    }

    /*
    * Inflates the menu bar.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.reset_chat_menu, menu)
    }

    /*
    * Assigns the reset function to its button.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset_chat -> resetChatBox()
            else -> super.onOptionsItemSelected(item)
        }
    }

    /*
    * Resets the chat history and ChatService instance.
    * Clears the message input and notifies the RecyclerView to rebind the items.
     */
    private fun resetChatBox() : Boolean {
        return if(chatBoxReady) {
            val itemCount = viewModel.getChatHistorySize()
            viewModel.clearChatHistory()
            chatService.reset()
            binding.messageInput.text?.clear()
            binding.chatHistory.adapter?.notifyItemRangeRemoved(0, itemCount)
            true
        } else {
            false
        }
    }

    /*
    * Processes user message input. Adds the new user message,
    * then generates a reply. Generates and audio reply and plays
    * it if TextToSpeech has been initialised correctly.
     */
    private fun inputReceived() {
        val message = binding.messageInput.text.toString()
        // Clear the chat input box.
        binding.messageInput.text?.clear()

        if(message.isNotEmpty()) {
            newMessage(message, ChatMessage.USER)
            // Generate reply with ChatService.
            val reply = chatService.getResponse(message)
            newMessage(reply, ChatMessage.AI)
            // Use TextToSpeech if ready.
            if(textToSpeechReady) {
                textToSpeech.speak(reply,
                    TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    /*
    * Adds a new message to the ModelView, then notifies the
    * RecyclerView to rebind the items.
     */
    private fun newMessage(message: String, sender: Int) {
        viewModel.addMessage(ChatMessage(message, sender))
        adapter.notifyItemInserted(0)
        recyclerView.scrollToPosition(0)
    }

}