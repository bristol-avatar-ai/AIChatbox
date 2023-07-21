package com.example.aichatbox

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
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
import androidx.core.app.ActivityCompat
import androidx.core.content.PackageManagerCompat.LOG_TAG
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.aichatbox.adapter.MessageAdapter
import com.example.aichatbox.data.ChatBoxViewModel
import com.example.aichatbox.databinding.FragmentChatBoxBinding
import com.example.aichatbox.model.ChatMessage
import com.example.aichatbox.model.ChatService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

const val TAG = "ChatBoxFragment"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

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

    // MediaRecorder class to record audio.
    private var mediaRecorder: MediaRecorder? = null
    private lateinit var recordingFile: File
    // Requesting permission to RECORD_AUDIO
    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    private var _binding: FragmentChatBoxBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        // if (!permissionToRecordAccepted) TODO: handle no permissions
    }

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

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Request audio permission.
        ActivityCompat.requestPermissions(requireActivity(), permissions, REQUEST_RECORD_AUDIO_PERMISSION)
        // Save the recordings to the cache.
        recordingFile = File.createTempFile("recording", ".3gp", requireContext().cacheDir)

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

        binding.recordButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                }
                MotionEvent.ACTION_UP -> {
                    stopRecording()
                    // convertAudioToString()
                    recordingFile.delete()
                }
            }
            false
        }

        setMessageEditExitListener()
    }

    /*
    * This hides the keyboard and stops editing messageInput when
    * the chatHistory RecyclerView is touched.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setMessageEditExitListener() {
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
        mediaRecorder?.release()
        mediaRecorder = null
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
    * Processes user message input. Adds the new
    * user message, then generates a reply.
     */
    private fun inputReceived() {
        val message = binding.messageInput.text.toString()
        // Clear the chat input box.
        binding.messageInput.text?.clear()

        if(message.isNotEmpty()) {
            addNewMessage(message, ChatMessage.USER)
            generateReply(message)
        }
    }

    /*
    * Adds a new message to the ViewModel, then notifies the
    * RecyclerView to rebind the items.
     */
    private fun addNewMessage(message: String, sender: Int) {
        viewModel.addMessage(
            ChatMessage(message, sender)
        )
        adapter.notifyItemInserted(0)
        recyclerView.scrollToPosition(0)
    }


    /*
    * Gets a response to the input message with ChatService. Generates an audio
    * reply and plays it if TextToSpeech has been initialised correctly.
    * Coroutines are use to prevent blocking the main thread.
     */
    private fun generateReply(message: String) {
        lifecycleScope.launch(Dispatchers.Default) {
            // Generate reply with ChatService.
            val reply = chatService.getResponse(message)

            // Use TextToSpeech if ready.
            if(textToSpeechReady) {
                textToSpeech.speak(reply,
                    TextToSpeech.QUEUE_FLUSH, null, null)
            }
            // Switch back to the main thread before updating the UI.
            withContext(Dispatchers.Main) {
                addNewMessage(reply, ChatMessage.AI)
            }
        }
    }

    private fun startRecording() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(recordingFile?.absolutePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            try {
                prepare()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to prepare MediaRecorder\n${e.stackTraceToString()}")
            }
            start()
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
    }

}