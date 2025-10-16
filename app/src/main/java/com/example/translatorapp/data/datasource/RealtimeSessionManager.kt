package com.example.translatorapp.data.datasource

import com.example.translatorapp.audio.AudioSessionController
import com.example.translatorapp.domain.model.LanguageDirection
import com.example.translatorapp.domain.model.TranslationContent
import com.example.translatorapp.domain.model.TranslationModelProfile
import com.example.translatorapp.domain.model.TranslationSession
import com.example.translatorapp.domain.model.TranslationSessionState
import com.example.translatorapp.domain.model.UserSettings
import com.example.translatorapp.network.ApiRelayService
import com.example.translatorapp.network.SessionMetricsRequest
import com.example.translatorapp.network.SessionStartRequest
import com.example.translatorapp.network.SessionUpdateRequest
import com.example.translatorapp.util.DispatcherProvider
import com.example.translatorapp.webrtc.WebRtcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeSessionManager @Inject constructor(
    private val apiRelayService: ApiRelayService,
    private val audioSessionController: AudioSessionController,
    private val webRtcClient: WebRtcClient,
    private val dispatcherProvider: DispatcherProvider
) : TranslationSession {

    private val coroutineScope = CoroutineScope(dispatcherProvider.io)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(TranslationSessionState())
    override val state = _state.asStateFlow()

    private val _transcripts = MutableSharedFlow<TranslationContent>(replay = 0)
    override val transcriptStream = _transcripts.asSharedFlow()

    private var sessionId: String? = null
    private var sessionJob: Job? = null
    private var lastAudioTimestamp: Instant? = null

    override suspend fun start(settings: UserSettings) {
        mutex.withLock {
            if (_state.value.isActive) return
            _state.value = _state.value.copy(
                direction = settings.direction,
                errorMessage = null
            )

            try {
                val response = apiRelayService.startSession(
                    SessionStartRequest(
                        direction = settings.direction.name,
                        model = settings.translationProfile.name,
                        offlineFallback = settings.offlineFallbackEnabled
                    )
                )
                sessionId = response.sessionId
                webRtcClient.createPeerConnection(emptyList())
                audioSessionController.startCapture { buffer ->
                    lastAudioTimestamp = Clock.System.now()
                    // TODO: stream buffer through WebRTC data channel
                }
                sessionJob = coroutineScope.launch {
                    webRtcClient.remoteAudio.collect { audioBytes ->
                        audioSessionController.playAudio(audioBytes)
                    }
                }
                _state.value = _state.value.copy(
                    isActive = true,
                    isMicrophoneOpen = true
                )
            } catch (t: Throwable) {
                sessionJob?.cancel()
                sessionJob = null
                audioSessionController.stopCapture()
                audioSessionController.releasePlayback()
                webRtcClient.close()
                sessionId = null
                _state.value = _state.value.copy(
                    isActive = false,
                    isMicrophoneOpen = false,
                    errorMessage = t.message ?: "Unable to start session"
                )
                throw t
            }
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            sessionJob?.cancel()
            audioSessionController.stopCapture()
            audioSessionController.releasePlayback()
            webRtcClient.close()
            sessionId?.let {
                runCatching { apiRelayService.stopSession() }
            }
            sessionId = null
            _state.value = TranslationSessionState()
        }
    }

    override suspend fun sendTextPrompt(prompt: String) {
        // Could be used for translation without microphone.
    }

    suspend fun toggleMicrophone(): Boolean = mutex.withLock {
        val newState = !_state.value.isMicrophoneOpen
        if (newState) {
            audioSessionController.startCapture { /* streaming handled elsewhere */ }
        } else {
            audioSessionController.stopCapture()
        }
        _state.value = _state.value.copy(isMicrophoneOpen = newState)
        newState
    }

    suspend fun updateDirection(direction: LanguageDirection) {
        mutex.withLock {
            _state.value = _state.value.copy(direction = direction)
            sessionId?.let {
                runCatching {
                    apiRelayService.updateSession(SessionUpdateRequest(direction = direction.name))
                }
            }
        }
    }

    suspend fun updateModel(profile: TranslationModelProfile) {
        sessionId?.let {
            runCatching {
                apiRelayService.updateSession(SessionUpdateRequest(model = profile.name))
            }
        }
    }

    suspend fun onTranslationReceived(content: TranslationContent) {
        _transcripts.emit(content)
        _state.value = _state.value.copy(currentSegment = content)
        sessionId?.let { id ->
            lastAudioTimestamp?.let { start ->
                val latency = (Clock.System.now() - start).inWholeMilliseconds
                runCatching {
                    apiRelayService.sendMetrics(
                        SessionMetricsRequest(
                            sessionId = id,
                            latency = latency
                        )
                    )
                }
                _state.value = _state.value.copy(
                    latencyMetrics = _state.value.latencyMetrics.copy(translationLatencyMs = latency)
                )
            }
        }
    }
}
