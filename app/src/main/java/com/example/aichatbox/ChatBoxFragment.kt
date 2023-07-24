package com.example.aichatbox

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.aichatbox.adapter.MessageAdapter
import com.example.aichatbox.audio.AudioRecorder
import com.example.aichatbox.data.ChatBoxViewModel
import com.example.aichatbox.databinding.FragmentChatBoxBinding
import com.example.aichatbox.model.ChatMessage
import com.example.aichatbox.model.ChatService
import com.example.aichatbox.network.TranscriptionApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

const val TAG = "ChatBoxFragment"

/**
 * Fragment containing chat interface.
 */
class ChatBoxFragment : Fragment(), OnInitListener, AudioRecorder.RecordingCompletionListener {

    // Attach chat history ViewModel. Delegate to viewModels to
    // retain its value through configuration changes.
    private val viewModel: ChatBoxViewModel by viewModels()

    // Initialise ChatService for message responses.
    private val chatService = ChatService()

    // Set to true to enable user chat input.
    private var chatBoxReady = false

    // TextToSpeech class for audio responses.
    private lateinit var textToSpeech: TextToSpeech

    // Set to true when TextToSpeech service is ready.
    private var textToSpeechReady = false
    private var voiceMode = true

    private var _binding: FragmentChatBoxBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter

    // True if audio recording permission has been granted.
    private var canRecordAudio = false
    /*
    * Save an instance of ActivityResultLauncher by registering
    * the permissions callback. This handles the user's response
    * to the system permissions dialog.
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Set canRecordAudio to true if permission is granted.
            canRecordAudio = it ?: false
        }
    // File to save audio recordings to.
    private lateinit var recordingFile: File
    private lateinit var audioRecorder: AudioRecorder

    /*
    * This function checks for audio recording permission
    * and requests it if missing.
     */
    private fun requestRecordAudioPermissionIfMissing() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is missing, request for it.
            requestRecordAudioPermission()
        }
    }

    /*
    * This function request for audio recording permission, displaying
    * a reason in a dialog box first if shouldShowRequestPermissionRationale
    * is true.
     */
    private fun requestRecordAudioPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            // Display a message explaining why the permission is required.
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.audio_permission_dialog_title)
                .setMessage(R.string.audio_permission_dialog_message)
                .setCancelable(false)
                .setPositiveButton(R.string.audio_permission_dialog_button) { _, _ ->
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                .show()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate fragment.
        _binding = FragmentChatBoxBinding.inflate(inflater, container, false)
        // Enable options menu.
        setHasOptionsMenu(true)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = binding.chatHistory
        // Create and assign adaptor to RecyclerView.
        adapter = MessageAdapter(viewModel)
        recyclerView.adapter = this.adapter
        // Improves RecyclerView performance, remove if RecyclerView dimensions can change.
        recyclerView.setHasFixedSize(true)

        // Request audio permission.
        requestRecordAudioPermissionIfMissing()
        // Save the recordings filepath to the cache directory.
        recordingFile = File.createTempFile("recording", ".ogg", requireContext().cacheDir)
        audioRecorder = AudioRecorder(requireContext(), recordingFile, lifecycleScope, this)

        // Initialise TextToSpeech
        textToSpeech = TextToSpeech(requireContext(), this)

        // Observe the messages LiveData, passing in the LifecycleOwner and the observer.
        viewModel.messages.observe(viewLifecycleOwner) {
            // Optional: Migrate adaptor updates here.
        }

        // Sends message when the send button is clicked.
        binding.actionButton.setOnClickListener {
            if (!voiceMode && chatBoxReady) {
                inputReceived()
            }
        }

        binding.actionButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (voiceMode) {
                        binding.messageInputLayout.hint = "Recording..."
                        binding.messageInput.isFocusable = false
                        audioRecorder.start()
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (voiceMode) {
                        audioRecorder.stop()
                        audioInputReceived()
                    }
                }
            }
            false
        }

        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // Also check for permissionToRecordAccepted
                voiceMode = if (binding.messageInput.text.isNullOrEmpty()) {
                    binding.actionButton.setImageResource(R.drawable.ic_speak_message)
                    true
                } else {
                    binding.actionButton.setImageResource(R.drawable.ic_send_message)
                    false
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        addHideKeyboardListener()
    }

    /*
    * This hides the keyboard and stops editing messageInput when
    * the chatHistory RecyclerView is touched.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun addHideKeyboardListener() {
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

    override fun onRecordingCompleted() {
        TODO("Not yet implemented")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatBoxReady = false
        textToSpeech.stop()
        textToSpeech.shutdown()
        textToSpeechReady = false
        audioRecorder.release()
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
    private fun resetChatBox(): Boolean {
        return if (chatBoxReady) {
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

        if (message.isNotEmpty()) {
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
            if (textToSpeechReady) {
                textToSpeech.speak(
                    reply,
                    TextToSpeech.QUEUE_FLUSH, null, null
                )
            }
            // Switch back to the main thread before updating the UI.
            withContext(Dispatchers.Main) {
                addNewMessage(reply, ChatMessage.AI)
            }
        }
    }


    private fun audioInputReceived() {
        binding.messageInputLayout.hint = "Processing..."
        lifecycleScope.launch {
            try {
                val message = TranscriptionApi.transcribe(recordingFile)
                recordingFile.delete()
                addNewMessage(message, ChatMessage.USER)
                generateReply(message)
                binding.messageInputLayout.hint = getString(R.string.send_message_hint)
                binding.messageInput.isFocusableInTouchMode = true
            } catch (e: Exception) {
                Log.d(TAG, e.stackTraceToString())
                // TODO: Handle this error
            }
        }
    }

}