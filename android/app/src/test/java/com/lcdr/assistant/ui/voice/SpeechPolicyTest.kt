package com.lcdr.assistant.ui.voice

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SpeechPolicyTest {
    private fun voice(name: String, locale: Locale = Locale.US, quality: Int = 300,
                      network: Boolean = false, installed: Boolean = true) =
        SpeechVoice(name, locale, quality, network, installed)

    @Test fun `offline voice wins even against higher quality network voice`() {
        assertEquals("local", selectSpeechVoice(listOf(voice("cloud", quality = 500, network = true),
            voice("local")), Locale.US)?.name)
    }
    @Test fun `no offline voice means no fallback`() {
        assertNull(selectSpeechVoice(listOf(voice("cloud", network = true)), Locale.US))
    }
    @Test fun `regional match wins over another accent`() {
        assertEquals("us", selectSpeechVoice(listOf(voice("gb", Locale.UK, 500),
            voice("us", quality = 200)), Locale.US)?.name)
    }
    @Test fun `quality then stable name resolves unordered voice sets`() {
        val voices = listOf(voice("z"), voice("a"), voice("low", quality = 100))
        assertEquals("a", selectSpeechVoice(voices, Locale.US)?.name)
        assertEquals("a", selectSpeechVoice(voices.reversed(), Locale.US)?.name)
    }
    @Test fun `missing and wrong language voices are excluded`() {
        assertNull(selectSpeechVoice(listOf(voice("missing", installed = false),
            voice("french", Locale.FRANCE)), Locale.US))
    }
    @Test fun `punctuated lines do not acquire an extra sentence stop`() {
        assertEquals("Hello. Next! Why?", SpeechText.clean("Hello.\nNext!\nWhy?"))
    }
    @Test fun `markdown links lists and emphasis retain readable text`() {
        assertEquals("Heading. First item. Read this link", SpeechText.clean(
            "# Heading\n- **First item**\n> Read [this](https://example.com) https://example.com"))
    }
    @Test fun `closed code fences are omitted without extra punctuation`() {
        assertEquals("Before. code omitted. After", SpeechText.clean("Before\n```kotlin\nsecret()\n```\nAfter"))
    }
    @Test fun `unclosed tilde code fence is omitted`() {
        assertEquals("Before. code omitted.", SpeechText.clean("Before\n~~~python\nsecret()"))
    }
    @Test fun `unclosed backtick code fence is omitted`() {
        assertFalse(SpeechText.clean("```\nsecret()\n").contains("secret"))
    }
    @Test fun `blockquote and list fenced code is omitted`() {
        assertEquals("code omitted.", SpeechText.clean("> ```kotlin\n> secret()\n> ```"))
        assertEquals("code omitted.", SpeechText.clean("- ```kotlin\n  secret()\n  ```"))
    }
    @Test fun `blank formatting has no spoken chunks`() {
        assertTrue(SpeechText.chunks("  \n***\n", Locale.US, 4000).isEmpty())
    }
    @Test fun `table separators and closing heading markers are not spoken`() {
        assertEquals("Status. Name Value. LCDR ready", SpeechText.clean(
            "## Status ##\n| Name | Value |\n| --- | :---: |\n| LCDR | ready |"))
    }
    @Test fun `nested URL parentheses and image syntax leave only the label`() {
        assertEquals("Read this. diagram", SpeechText.clean(
            "Read [this](https://example.com/a_(b))\n![diagram](https://example.com/x.png)"))
    }
    @Test fun `sentence chunks preserve decimal and quoted punctuation`() {
        val chunks = SpeechText.chunks("Cost is 3.14. \"Ready!\" Next.", Locale.US, 4000)
        assertEquals("Cost is 3.14.", chunks.first())
        assertEquals("Next.", chunks.last())
        assertEquals("Cost is 3.14. \"Ready!\" Next.", chunks.joinToString(" "))
    }
    @Test fun `oversized sentence stays within engine limit without losing characters`() {
        val input = "a".repeat(3999) + "😀" + "b".repeat(4010)
        val chunks = SpeechText.chunks(input, Locale.US, 4000)
        assertTrue(chunks.all { it.length <= 4000 })
        assertTrue(chunks.none { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
        assertEquals(input, chunks.joinToString(""))
    }
}
