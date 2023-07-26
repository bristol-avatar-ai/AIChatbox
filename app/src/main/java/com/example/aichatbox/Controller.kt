package com.example.aichatbox

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.aichatbox.audio.AudioRecorder
import com.example.aichatbox.const.Language
import com.example.aichatbox.model.ChatService
import java.io.File

private const val RECORDING_NAME = "recording"
private const val RECORDING_FILE_TYPE = "ogg"

/**
 * The Controller serves as a wrapper for all non-UI related classes
 * and variables in the ChatBox Fragment.
 */
class Controller(
    listener: ChatBoxFragment,
    context: Context,
    lifecycleCoroutineScope: LifecycleCoroutineScope
) {

    // Used to switch between typing and speech mode.
    var speechMode = true
    var language = Language.English

    // Initialise ChatService for message responses.
    val chatService = ChatService()

    // Initialise TextToSpeech class for audio responses.
    val textToSpeech: TextToSpeech = TextToSpeech(context, listener)

    // Set to true when TextToSpeech service is ready.
    var textToSpeechReady = false

    // True if audio recording permission has been granted.
    var recordAudioReady = false

    // Save the recordings filepath to the cache directory.
    val recordingFile: File =
        File.createTempFile(RECORDING_NAME, RECORDING_FILE_TYPE, context.cacheDir)

    // Initialise AudioRecorder class for recording audio input.
    val audioRecorder: AudioRecorder =
        AudioRecorder(context, recordingFile, lifecycleCoroutineScope, listener)

    /*
    * This function releases all resources initialised by the Controller
    * and should be called in onDestroy/onDestroyView of the enclosing
    * Activity/Fragment.
     */
    fun release() {
        recordAudioReady = false
        chatService.reset()
        audioRecorder.release()
        textToSpeech.stop()
        textToSpeech.shutdown()
        textToSpeechReady = false
    }

}