package com.lcdr.assistant.ui.voice

internal interface SpeechOutput {
    fun stop()
    fun speak(text: String, id: String, flush: Boolean): Boolean
    fun pause(millis: Long, id: String): Boolean
}

/** All methods run on Main; Android listener callbacks must be dispatched there. */
internal class SpeechPlayback(
    private val output: SpeechOutput,
    private val finished: (String?) -> Unit
) {
    private var generation = 0L
    private var pending = emptySet<String>()
    private var lastId: String? = null

    fun cancel() {
        // Invalidate first: stop/flush may produce callbacks for the discarded queue.
        pending = emptySet()
        lastId = null
        output.stop()
    }

    fun speak(sentences: List<String>) {
        cancel()
        val token = ++generation
        if (sentences.isEmpty()) {
            finished(null)
            return
        }
        val speechIds = sentences.indices.map { "lcdr_${token}_speech_$it" }
        val pauseIds = (0 until sentences.lastIndex).map { "lcdr_${token}_pause_$it" }
        pending = (speechIds + pauseIds).toSet()
        lastId = speechIds.last()
        for ((index, sentence) in sentences.withIndex()) {
            if (!pending.contains(speechIds[index])) return
            if (!output.speak(sentence, speechIds[index], index == 0)) {
                error(speechIds[index])
                return
            }
            if (index < sentences.lastIndex && pending.contains(pauseIds[index]) &&
                !output.pause(120, pauseIds[index])) {
                error(pauseIds[index])
                return
            }
        }
    }

    fun done(id: String?) {
        if (id != null && id == lastId) finish(null)
    }

    fun error(id: String?) {
        if (id in pending) finish("Speech playback failed")
    }

    fun stopped(id: String?) {
        if (id in pending) finish(null)
    }

    private fun finish(error: String?) {
        cancel()
        finished(error)
    }
}
