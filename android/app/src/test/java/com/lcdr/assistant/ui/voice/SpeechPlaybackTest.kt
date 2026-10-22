package com.lcdr.assistant.ui.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechPlaybackTest {
    private class Engine : SpeechOutput {
        data class Call(val text: String?, val id: String, val flush: Boolean, val pause: Long? = null)
        val calls = mutableListOf<Call>()
        var stops = 0
        var failAt = -1
        override fun stop() { stops++ }
        override fun speak(text: String, id: String, flush: Boolean): Boolean {
            calls += Call(text, id, flush)
            return calls.size != failAt
        }
        override fun pause(millis: Long, id: String): Boolean {
            calls += Call(null, id, false, millis)
            return calls.size != failAt
        }
    }
    @Test fun `queues sentences and only intervening 120ms pauses`() {
        val engine = Engine()
        val results = mutableListOf<String?>()
        val playback = SpeechPlayback(engine, results::add)
        playback.speak(listOf("First.", "Second."))
        assertEquals(listOf("First.", null, "Second."), engine.calls.map { it.text })
        assertEquals(listOf(true, false, false), engine.calls.map { it.flush })
        assertEquals(120L, engine.calls[1].pause)
        playback.done(engine.calls[0].id)
        playback.done(engine.calls[1].id)
        assertTrue(results.isEmpty())
        playback.done(engine.calls.last().id)
        playback.done(engine.calls.last().id)
        assertEquals(listOf<String?>(null), results)
    }
    @Test fun `old final and error and stop callbacks cannot finish replacement playback`() {
        val engine = Engine()
        val results = mutableListOf<String?>()
        val playback = SpeechPlayback(engine, results::add)
        playback.speak(listOf("Old."))
        val old = engine.calls.last().id
        playback.speak(listOf("New."))
        val current = engine.calls.last().id
        assertNotEquals(old, current)
        playback.done(old)
        playback.error(old)
        playback.stopped(old)
        playback.error(null)
        assertTrue(results.isEmpty())
        playback.done(current)
        assertEquals(1, results.size)
    }
    @Test fun `cancel invalidates callbacks before engine stop`() {
        val engine = Engine()
        val results = mutableListOf<String?>()
        val playback = SpeechPlayback(engine, results::add)
        playback.speak(listOf("Hello."))
        val id = engine.calls.last().id
        playback.cancel()
        playback.done(id)
        playback.error(id)
        playback.stopped(id)
        assertTrue(results.isEmpty())
        assertTrue(engine.stops > 0)
    }
    @Test fun `speech and silence queue rejection finish with error and discard remainder`() {
        for (failure in 1..3) {
            val engine = Engine().apply { failAt = failure }
            val results = mutableListOf<String?>()
            val playback = SpeechPlayback(engine, results::add)
            playback.speak(listOf("First.", "Second.", "Third."))
            assertEquals(failure, engine.calls.size)
            assertEquals(1, results.size)
            assertNotNull(results.single())
            assertTrue(engine.stops >= 2)
        }
    }
    @Test fun `engine error on any sentence or pause stops whole queue and allows recovery`() {
        for (index in 0..2) {
            val engine = Engine()
            val results = mutableListOf<String?>()
            val playback = SpeechPlayback(engine, results::add)
            playback.speak(listOf("First.", "Second."))
            val oldFinal = engine.calls.last().id
            playback.error(engine.calls[index].id)
            playback.done(oldFinal)
            assertEquals(1, results.size)
            assertNotNull(results.single())
            playback.speak(listOf("Recovered."))
            playback.done(engine.calls.last().id)
            assertEquals(listOf("Speech playback failed", null), results)
        }
    }
    @Test fun `engine stop finishes current playback once`() {
        val engine = Engine()
        val results = mutableListOf<String?>()
        val playback = SpeechPlayback(engine, results::add)
        playback.speak(listOf("First.", "Second."))
        playback.stopped(engine.calls.first().id)
        playback.done(engine.calls.last().id)
        assertEquals(1, results.size)
    }
    @Test fun `empty playback stops old output and finishes immediately`() {
        val engine = Engine()
        val results = mutableListOf<String?>()
        val playback = SpeechPlayback(engine, results::add)
        playback.speak(listOf("Old."))
        playback.speak(emptyList())
        playback.done(engine.calls.last().id)
        assertEquals(listOf<String?>(null), results)
    }
}
