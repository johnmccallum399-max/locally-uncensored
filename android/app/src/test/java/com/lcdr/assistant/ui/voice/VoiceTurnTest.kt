package com.lcdr.assistant.ui.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceTurnTest {
    @Test fun `cancel rejects late results and response events`() {
        val turn = VoiceTurn()
        val old = turn.begin()
        turn.cancel()
        assertFalse(turn.acceptRecognition(old))
        assertFalse(turn.isCurrent(old))
    }
    @Test fun `new turn rejects previous turn callbacks`() {
        val turn = VoiceTurn()
        val old = turn.begin()
        val current = turn.begin()
        assertFalse(turn.isCurrent(old))
        assertFalse(turn.acceptRecognition(old))
        assertTrue(turn.isCurrent(current))
        assertTrue(turn.acceptRecognition(current))
    }
    @Test fun `recognition result is consumed once but response stays current`() {
        val turn = VoiceTurn()
        val token = turn.begin()
        assertTrue(turn.isListening(token))
        assertTrue(turn.acceptRecognition(token))
        assertFalse(turn.isListening(token))
        assertFalse(turn.acceptRecognition(token))
        assertTrue(turn.isCurrent(token))
    }
}
