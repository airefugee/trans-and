package com.example.translatorapp.network

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeApi @Inject constructor(
    private val service: ApiRelayService
) {
    suspend fun startSession(request: SessionStartRequest): SessionStartResponse =
        service.startSession(request)

    suspend fun sendSdpAnswer(sessionId: String, sdp: String) {
        service.updateSession(
            SessionUpdateRequest(
                sessionId = sessionId,
                webrtcAnswer = sdp
            )
        )
    }

    suspend fun updateSession(
        sessionId: String,
        direction: String? = null,
        model: String? = null
    ) {
        service.updateSession(
            SessionUpdateRequest(
                sessionId = sessionId,
                direction = direction,
                model = model
            )
        )
    }

    suspend fun stopSession(sessionId: String) {
        service.stopSession(SessionStopRequest(sessionId))
    }

    suspend fun sendMetrics(sessionId: String, latency: Long, errorCode: String? = null) {
        service.sendMetrics(
            SessionMetricsRequest(
                sessionId = sessionId,
                latency = latency,
                errorCode = errorCode
            )
        )
    }
}
