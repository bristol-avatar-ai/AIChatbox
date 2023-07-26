package com.example.aichatbox.audio

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.lang.Exception
import kotlin.math.max

private const val TAG = "AudioRecorder"

// Audio file format and encoder.
private const val OUTPUT_FORMAT = MediaRecorder.OutputFormat.OGG
private const val AUDIO_ENCODER = MediaRecorder.AudioEncoder.OPUS

// The maximum recording duration (in milliseconds) before stop() is
// automatically called.
private const val MAXIMUM_RECORDING_TIME = 30000L

// The minimum required delay (in milliseconds) between start and stop
// calls.
private const val MINIMUM_STOP_DELAY = 300L

/**
 * The AudioRecorder manages an instance of MediaRecorder to record audio
 * with the start() and stop() functions. release() should be called on
 * shutdown to ensure that the MediaRecorder has been released correctly.
 * RecordingCompletionListener informs when the recording has completed.
 */
class AudioRecorder(
    private val context: Context,
    private val recordingFile: File,
    private val lifecycleCoroutineScope: LifecycleCoroutineScope,
    private val recordingCompletionListener: RecordingCompletionListener
) {

    // Callback to inform when the recording has completed.
    interface RecordingCompletionListener {
        fun onRecordingCompleted()
    }

    // Instance of MediaRecorder class used to record audio.
    private var mediaRecorder: MediaRecorder? = null
    private var startTime = 0L

    // Job used to automatically stop recording after a set delay.
    private var autoStopJob: Job? = null

    // Flag to prevent multiple onRecordingCompleted callbacks.
    private var isRecordingCompleted = false

    /*
    * This function initialises, configures, prepares, and starts the
    * instance of MediaRecorder. A Job is scheduled to stop the recording
    * after MAXIMUM_RECORDING_TIME. The returned Boolean indicates if
    * the MediaRecorder was prepared (and started) successfully.
     */
    fun start() {
        isRecordingCompleted = false
        mediaRecorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(OUTPUT_FORMAT)
            setAudioEncoder(AUDIO_ENCODER)
            setOutputFile(recordingFile.absolutePath)
            try { prepare() } catch (e: Exception) {
                Log.e(TAG, "start: failed to prepare MediaRecorder", e)
                throw e
            }
            try { start() } catch (e: Exception) {
                Log.e(TAG, "start: failed to start MediaRecorder", e)
                throw e
            }
            startTime = System.currentTimeMillis()
            // Automatically stop recording after MAXIMUM_RECORDING_TIME.
            autoStopJob = lifecycleCoroutineScope.launch {
                delay(MAXIMUM_RECORDING_TIME)
                stop()
            }
        }
    }

    /*
    * This function stops and releases the MediaRecorder after a
    * minimum delay of MINIMUM_STOP_DELAY from starting it.
     */
    fun stop() {
        // Cancel the auto-stopping job if it's still active.
        autoStopJob?.cancel()

        // Stop recording with a minimum delay.
        lifecycleCoroutineScope.launch {
            delay(getStopDelay(startTime))
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            // Callback to inform that the recording has completed.
            if (!isRecordingCompleted) {
                // This check prevents multiple callbacks.
                isRecordingCompleted = true
                recordingCompletionListener.onRecordingCompleted()
            }
        }
    }

    /*
    * This function calculates the delay (in milliseconds) required to
    * ensure that stop is called at least MINIMUM_STOP_DELAY after start.
     */
    private fun getStopDelay(startTime: Long): Long {
        return max(
            MINIMUM_STOP_DELAY - System.currentTimeMillis() + startTime,
            0
        )
    }

    /*
    * This function ensure that the MediaRecorder has been
    * released correctly.
     */
    fun release() {
        mediaRecorder?.release()
        mediaRecorder = null
    }
}