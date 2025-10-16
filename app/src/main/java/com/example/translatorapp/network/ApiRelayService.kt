package com.example.translatorapp.network

import retrofit2.http.Body
import retrofit2.http.POST

interface ApiRelayService {
    @POST("session/start")
    suspend fun startSession(@Body body: SessionStartRequest): SessionStartResponse

    @POST("session/update")
    suspend fun updateSession(@Body body: SessionUpdateRequest)

    @POST("session/stop")
    suspend fun stopSession(@Body body: SessionStopRequest)

    @POST("session/metrics")
    suspend fun sendMetrics(@Body body: SessionMetricsRequest)
}
