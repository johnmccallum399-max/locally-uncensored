package com.lcdr.assistant.ui.voice

import java.text.BreakIterator
import java.util.Locale

internal data class SpeechVoice(
    val name: String,
    val locale: Locale,
    val quality: Int,
    val network: Boolean,
    val installed: Boolean
)

/** No implicit cloud fallback. Stable for the same installed voice inventory. */
internal fun selectSpeechVoice(voices: List<SpeechVoice>, locale: Locale): SpeechVoice? =
    voices.filter { it.installed && !it.network && it.locale.language == locale.language }
        .sortedWith(compareByDescending<SpeechVoice> { it.locale == locale }
            .thenByDescending { it.locale.country == locale.country }
            .thenByDescending { it.locale.script == locale.script }
            .thenByDescending { it.quality }
            .thenBy { it.name })
        .firstOrNull()

internal object SpeechText {
    fun clean(text: String): String {
        var fence: Char? = null
        var fenceLength = 0
        val lines = mutableListOf<String>()
        text.lineSequence().forEach { line ->
            val containerContent = line
                .replace(Regex("^\\s*(?:>\\s*)+"), "")
                .replace(Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+"), "")
            val marker = Regex("^ {0,3}(`{3,}|~{3,})(.*)$").find(containerContent)
            if (fence != null) {
                if (marker != null && marker.groupValues[1].first() == fence &&
                    marker.groupValues[1].length >= fenceLength && marker.groupValues[2].isBlank()) {
                    fence = null
                }
            } else if (marker != null) {
                fence = marker.groupValues[1].first()
                fenceLength = marker.groupValues[1].length
                lines += "code omitted."
            } else {
                if (Regex("^\\s*\\|?\\s*:?-{3,}:?(?:\\s*\\|\\s*:?-{3,}:?)*\\s*\\|?\\s*$").matches(line)) return@forEach
                val cleaned = line
                    .replace(Regex("`([^`]*)`"), "$1")
                    .replace(Regex("!?\\[([^]]*)]\\((?:[^()]|\\([^()]*\\))*\\)"), "$1")
                    .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "link")
                    .replace(Regex("^\\s{0,3}#{1,6}\\s+"), "")
                    .replace(Regex("\\s+#+\\s*$"), "")
                    .replace(Regex("^\\s*(?:>\\s*)+"), "")
                    .replace(Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+"), "")
                    .replace('|', ' ')
                    .replace(Regex("[*~]+"), "")
                    .replace(Regex("(?<!\\w)_+|_+(?!\\w)"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (cleaned.isNotEmpty()) lines += cleaned
            }
        }
        return buildString {
            lines.forEach { line ->
                if (isNotEmpty()) {
                    if (last() !in ".!?:\"'”’)") append('.')
                    append(' ')
                }
                append(line)
            }
        }
    }

    fun chunks(text: String, locale: Locale, maxLength: Int): List<String> {
        require(maxLength >= 2)
        val cleaned = clean(text)
        if (cleaned.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val boundaries = BreakIterator.getSentenceInstance(locale).apply { setText(cleaned) }
        var start = boundaries.first()
        var end = boundaries.next()
        while (end != BreakIterator.DONE) {
            var rest = cleaned.substring(start, end).trim()
            while (rest.length > maxLength) {
                var cut = rest.lastIndexOf(' ', maxLength).takeIf { it > 0 } ?: maxLength
                if (rest[cut - 1].isHighSurrogate() && rest[cut].isLowSurrogate()) cut--
                result += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotEmpty()) result += rest
            start = end
            end = boundaries.next()
        }
        return result
    }
}
