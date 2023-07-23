package com.example.aichatbox.network

/**
 * Custom exception classes for TranscriptionApiService.
 */
open class TranscriptionAPIServiceException(message: String) : Exception(message) {
    class InvalidFileSizeException() :
        TranscriptionAPIServiceException("Audio files must must be within 100B-100MB.")

    class TimeoutException() :
        TranscriptionAPIServiceException("Speech to Text POST request timed out.")

    class HttpException() :
        TranscriptionAPIServiceException("Speech to Text HTTP error received.")

    class InvalidFileException() :
        TranscriptionAPIServiceException("An error occurred when trying to read the audio file.")
}