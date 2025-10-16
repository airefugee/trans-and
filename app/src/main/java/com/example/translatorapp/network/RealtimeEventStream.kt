package com.example.translatorapp.network

import com.example.translatorapp.domain.model.TranslationContent
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

@Singleton
class RealtimeEventStream @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val apiConfig: ApiConfig
) {
    fun listen(sessionId: String, token: String): Flow<TranslationContent> = callbackFlow {
        val scope = this
        val isClosed = AtomicBoolean(false)
        val url = buildUrl(sessionId, token)
        var currentDelayMs = INITIAL_RETRY_DELAY_MS
        var reconnectJob: Job? = null
        var activeSocket: WebSocket? = null

        fun scheduleReconnect() {
            if (isClosed.get()) return
            val delayMs = currentDelayMs
            reconnectJob?.cancel()
            reconnectJob = launch {
                delay(delayMs)
                if (isClosed.get() || !isActive) return@launch
                connect()
            }
            currentDelayMs = (currentDelayMs * RETRY_MULTIPLIER).toLong()
                .coerceAtMost(MAX_RETRY_DELAY_MS)
        }

        fun connect() {
            val request = Request.Builder().url(url).build()
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    currentDelayMs = INITIAL_RETRY_DELAY_MS
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleMessage(bytes.utf8())
                }

                private fun handleMessage(message: String) {
                    parseTranslation(message)?.let { translation ->
                        scope.trySendBlocking(translation)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (isClosed.get()) {
                        scope.close()
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (isClosed.get()) {
                        scope.close()
                    } else {
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (isClosed.get()) {
                        scope.close(t)
                    } else {
                        scheduleReconnect()
                    }
                }
            }
            activeSocket = okHttpClient.newWebSocket(request, listener)
        }

        connect()

        awaitClose {
            isClosed.set(true)
            reconnectJob?.cancel()
            activeSocket?.close(NORMAL_CLOSURE_STATUS, null)
        }
    }

    private fun buildUrl(sessionId: String, token: String): HttpUrl {
        val base = apiConfig.baseUrl.toHttpUrl()
        val scheme = if (base.isHttps) SECURE_WEBSOCKET_SCHEME else WEBSOCKET_SCHEME
        return base.newBuilder()
            .scheme(scheme)
            .addPathSegment("session")
            .addPathSegment("events")
            .addQueryParameter("sessionId", sessionId)
            .addQueryParameter("token", token)
            .build()
    }

    private fun parseTranslation(payload: String): TranslationContent? {
        return runCatching {
            val envelope = json.decodeFromString(RelayEventDto.serializer(), payload)
            if (envelope.type !in TRANSLATION_EVENT_TYPES) {
                return null
            }
            val data = envelope.data ?: return null
            val translation = json.decodeFromJsonElement(TranslationPayloadDto.serializer(), data)
            if (translation.transcript.isNullOrBlank() && translation.translation.isNullOrBlank()) {
                return null
            }
            TranslationContent(
                transcript = translation.transcript.orEmpty(),
                translation = translation.translation.orEmpty(),
                synthesizedAudioPath = translation.audioUrl
            )
        }.getOrNull()
    }

    companion object {
        private const val WEBSOCKET_SCHEME = "ws"
        private const val SECURE_WEBSOCKET_SCHEME = "wss"
        private const val NORMAL_CLOSURE_STATUS = 1000
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val RETRY_MULTIPLIER = 2.0
        private val TRANSLATION_EVENT_TYPES = setOf(
            "translation",
            "translation.partial",
            "translation.final",
            "transcript.final"
        )
    }
}

@Serializable
private data class RelayEventDto(
    @SerialName("type") val type: String,
    @SerialName("data") val data: JsonElement? = null
)

@Serializable
private data class TranslationPayloadDto(
    @SerialName("transcript") val transcript: String? = null,
    @SerialName("translation") val translation: String? = null,
    @SerialName("audioUrl") val audioUrl: String? = null
)
