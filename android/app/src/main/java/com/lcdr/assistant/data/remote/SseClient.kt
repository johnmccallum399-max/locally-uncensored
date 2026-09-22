package com.lcdr.assistant.data.remote

import com.google.gson.Gson
import com.lcdr.assistant.data.remote.dto.SseChunk
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Response
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

sealed class SseEvent {
    data class Chunk(val chunk: SseChunk) : SseEvent()
    data class RawData(val data: String) : SseEvent()
    data class Error(val throwable: Throwable) : SseEvent()
    object Done : SseEvent()
}

@Singleton
class SseClient @Inject constructor(private val gson: Gson) {

    fun parseStream(response: Response<ResponseBody>): Flow<SseEvent> = callbackFlow {
        val body = response.body() ?: run {
            trySend(SseEvent.Error(IllegalStateException("Empty SSE response")))
            close()
            return@callbackFlow
        }

        try {
            body.source().use { source ->
                val buffer = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.startsWith("data: ") -> {
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") {
                                trySend(SseEvent.Done)
                                break
                            }
                            try {
                                val chunk = gson.fromJson(data, SseChunk::class.java)
                                trySend(SseEvent.Chunk(chunk))
                            } catch (_: Exception) {
                                trySend(SseEvent.RawData(data))
                            }
                        }
                        line.isEmpty() -> {
                            // event boundary — no-op
                        }
                        line.startsWith("event: done") || line == "event: message_stop" -> {
                            trySend(SseEvent.Done)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            trySend(SseEvent.Error(e))
        } finally {
            close()
        }

        awaitClose { body.close() }
    }
}
