package com.example.translatorapp.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionStartRequest(
    @SerialName("direction") val direction: String,
    @SerialName("model") val model: String,
    @SerialName("offlineFallback") val offlineFallback: Boolean
)

@Serializable
data class SessionStartResponse(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("webrtcSdp") val webrtcSdp: String,
    @SerialName("token") val token: String,
    @SerialName("iceServers") val iceServers: List<IceServerDto> = emptyList()
)

@Serializable
data class SessionUpdateRequest(
    @SerialName("sessionId") val sessionId: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("direction") val direction: String? = null,
    @SerialName("webrtcAnswer") val webrtcAnswer: String? = null
)

@Serializable
data class SessionMetricsRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("latency") val latency: Long,
    @SerialName("errorCode") val errorCode: String? = null
)

@Serializable
data class SessionStopRequest(
    @SerialName("sessionId") val sessionId: String
)

@Serializable
data class IceServerDto(
    @SerialName("urls") val urls: List<String>,
    @SerialName("username") val username: String? = null,
    @SerialName("credential") val credential: String? = null
)
