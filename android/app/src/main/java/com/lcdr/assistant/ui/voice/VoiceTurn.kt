package com.lcdr.assistant.ui.voice

/** Main-thread ownership of recognizer callbacks and the corresponding response. */
internal class VoiceTurn {
    private var generation = 0L
    private var listening = false
    fun begin(): Long {
        listening = true
        return ++generation
    }
    fun cancel() {
        generation++
        listening = false
    }
    fun isCurrent(token: Long): Boolean = token == generation
    fun isListening(token: Long): Boolean = isCurrent(token) && listening
    fun acceptRecognition(token: Long): Boolean {
        if (!isListening(token)) return false
        listening = false
        return true
    }
}
