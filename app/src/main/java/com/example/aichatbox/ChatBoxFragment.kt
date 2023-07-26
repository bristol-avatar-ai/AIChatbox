package com.example.aichatbox

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.aichatbox.network.TranscriptionAPIServiceException
import com.example.aichatbox.network.TranscriptionApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "ChatBoxFragment"

/**
 * Fragment containing chat interface.
 */
class ChatBoxFragment : Fragment(), OnInitListener, AudioRecorder.RecordingCompletionListener {

    private var _binding: FragmentChatBoxBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter

    // Attach chat history ViewModel. Delegate to viewModels to
    // retain its value through configuration changes.
    private val viewModel: ChatBoxViewModel by viewModels()

    private lateinit var controller: Controller

    /*
    * Save an instance of ActivityResultLauncher by registering
    * the permissions callback. This handles the user's response
    * to the system permissions dialog.
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Set recordAudioReady to true if permission is granted.
            controller.recordAudioReady = it ?: false
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = binding.chatHistory
        // Create and assign adaptor to RecyclerView.
        adapter = MessageAdapter(viewModel)
        recyclerView.adapter = this.adapter
        // Improves RecyclerView performance, remove if RecyclerView dimensions can change.
        recyclerView.setHasFixedSize(true)

        controller = Controller(this, requireContext(), lifecycleScope)
        // Request audio permission.
        requestRecordAudioPermissionIfMissing()

        // Observe the messages LiveData, passing in the LifecycleOwner and the observer.
        viewModel.messages.observe(viewLifecycleOwner) {
            // Optional: Migrate adaptor updates here.
        }

        addSpeechModeListener()
        addHideKeyboardListener()
    }

    /*
    * This function checks for audio recording permission
    * and requests it if missing.
     */
    private fun requestRecordAudioPermissionIfMissing() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            controller.recordAudioReady = true
        } else {
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

    /*
    * This listener hides the keyboard and clears focus from messageInput
    * when the chatHistory RecyclerView is touched.
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
    * This listener enables/disables speech mode depending on whether
    * the message input field in in focus. The action button icon
    * changes accordingly.
     */
    private fun addSpeechModeListener() {
        binding.messageInput.setOnFocusChangeListener { _, hasFocus ->
            controller.speechMode = if (hasFocus) {
                binding.actionButton.setImageResource(R.drawable.ic_send_message)
                false
            } else {
                binding.actionButton.setImageResource(R.drawable.ic_speak_message)
                true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controller.release()
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
    * onInit is called when the TextToSpeech service is initialised.
    * Initialisation errors are handles and the language is set.
    * User input is disabled by default and enabled here.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setTextToSpeechLanguage()
        } else {
            Log.e(TAG, "Failed to initialise TextToSpeech\n$status")
            replyWithMessage(R.string.speech_error_message)
        }
        // Disable visibility of the loading animation after initialisation.
        binding.loadingAnimation.visibility = View.INVISIBLE
        enableTextInput()
        enableSpeechInput()
    }

    /*
    * This function sets the TextToSpeech language and handles any errors.
     */
    private fun setTextToSpeechLanguage() {
        val result = controller.textToSpeech.setLanguage(Locale.UK)
        if (result == TextToSpeech.LANG_MISSING_DATA
            || result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.e(TAG, "Failed to set TextToSpeech language\n$result")
            replyWithMessage(R.string.speech_error_message)
        } else {
            controller.textToSpeechReady = true
        }
    }

    /*
    * Set a listener for the action button in text mode.
    * Sends the text in messageInput if not in speech mode.
     */
    private fun enableTextInput() {
        binding.actionButton.setOnClickListener {
            if (!controller.speechMode) {
                inputReceived()
            }
        }
    }

    /*
    * Set a listener for the action button in speech mode.
    * Starts recording when the button is pressed and stops
    * recording when it is released.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun enableSpeechInput() {
        binding.actionButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (controller.recordAudioReady
                        && controller.speechMode
                    ) {
                        startAudioRecorder()
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (controller.recordAudioReady
                        && controller.speechMode
                    ) {
                        controller.audioRecorder.stop()
                    }
                }
            }
            false
        }
    }

    /*
    * This function starts the AudioRecorder, then disables
    * text input and changes the hint.
     */
    private fun startAudioRecorder() {
        try {
            controller.audioRecorder.start()
            binding.messageInputLayout.hint =
                getString(R.string.recording_message)
            binding.messageInput.isFocusable = false
        } catch (_: Exception) {
            replyWithMessage(R.string.recording_error_message)
        }
    }

    /*
    * Resets the chat history and ChatService instance.
    * Clears the message input and notifies the RecyclerView to rebind the items.
     */
    private fun resetChatBox(): Boolean {
        val itemCount = viewModel.getChatHistorySize()
        viewModel.clearChatHistory()
        controller.chatService.reset()
        binding.messageInput.text?.clear()
        binding.chatHistory.adapter?.notifyItemRangeRemoved(0, itemCount)
        return true
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
    * Coroutines are used to prevent blocking the main thread.
     */
    private fun generateReply(message: String) {
        lifecycleScope.launch {
            // Generate reply with ChatService.
            val reply = controller.chatService.getResponse(message)
            readMessage(reply)

            // Switch back to the main thread before updating the UI.
            withContext(Dispatchers.Main) {
                addNewMessage(reply, ChatMessage.AI)
            }
        }
    }

    /*
    * Reads message with TextToSpeech if ready.
     */
    private fun readMessage(message: String) {
        if (controller.textToSpeechReady) {
            controller.textToSpeech.speak(
                message,
                TextToSpeech.QUEUE_FLUSH, null, null
            )
        }
    }

    /*
    * onRecordingCompleted is called when the AudioRecorder stops recording.
    * The action button is disables and the hint is modified. A reply is
    * generated before the control and hint are reset.
     */
    override fun onRecordingCompleted() {
        binding.messageInputLayout.hint = getString(R.string.processing_message)
        binding.actionButton.isEnabled = false

        lifecycleScope.launch {
            replyToSpeech()
            binding.messageInputLayout.hint = getString(R.string.send_message_hint)
            binding.messageInput.isFocusableInTouchMode = true
            binding.actionButton.isEnabled = true
        }
    }

    /*
    * This function transcribes the recordingFile into text, deletes the
    * file, and then generates a reply.
     */
    private suspend fun replyToSpeech() {
        try {
            val message = TranscriptionApi.transcribe(controller.recordingFile)
            controller.recordingFile.delete()
            addNewMessage(message, ChatMessage.USER)
            generateReply(message)
        } catch (e: TranscriptionAPIServiceException.NoInternetException) {
            replyWithMessage(R.string.network_error_message)
        } catch (e: Exception) {
            replyWithMessage(R.string.error_message)
        }
    }

    /*
    * This function replies to the user with a message by text
    * and speech (if enabled).
     */
    private fun replyWithMessage(resId: Int) {
        addNewMessage(getString(resId), ChatMessage.AI)
        readMessage(getString(resId))
    }

}