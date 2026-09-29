package com.lcdr.assistant.data.remote

import com.lcdr.assistant.data.remote.dto.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    // Memory
    @GET("api/memory")
    suspend fun getMemories(@Header("Authorization") token: String): Response<MemoryListResponse>

    @POST("api/memory")
    suspend fun remember(
        @Header("Authorization") token: String,
        @Body request: RememberRequest
    ): Response<MemoryDto>

    @DELETE("api/memory/{id}")
    suspend fun deleteMemory(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

    // Chat (streaming — raw ResponseBody, parsed by SseClient)
    @POST("api/chat")
    @Streaming
    suspend fun chatStream(
        @Header("Authorization") token: String,
        @Header("Accept") accept: String = "text/event-stream",
        @Body request: ChatRequest
    ): Response<ResponseBody>

    // Continue chat after tool results
    @POST("api/chat/tool-results")
    @Streaming
    suspend fun continueWithToolResults(
        @Header("Authorization") token: String,
        @Header("Accept") accept: String = "text/event-stream",
        @Body request: ToolResultRequest
    ): Response<ResponseBody>

    // Hub
    @GET("api/hub/agents")
    suspend fun getHubAgents(@Header("Authorization") token: String): Response<HubAgentsResponse>

    @POST("api/hub/sessions")
    suspend fun createHubSession(
        @Header("Authorization") token: String,
        @Body request: HubSessionRequest
    ): Response<HubSessionDto>

    @GET("api/hub/sessions/{sessionId}")
    suspend fun getHubSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String
    ): Response<HubSessionDto>

    @GET("api/hub/sessions/{sessionId}/stream")
    @Streaming
    suspend fun streamHubSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String
    ): Response<ResponseBody>
}
