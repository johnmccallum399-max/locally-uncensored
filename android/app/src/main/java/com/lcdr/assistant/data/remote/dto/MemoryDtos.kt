package com.lcdr.assistant.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MemoryDto(
    val id: String,
    val key: String,
    val value: String,
    @SerializedName("created_at") val createdAt: Long = 0L,
    @SerializedName("updated_at") val updatedAt: Long = 0L
)

data class MemoryListResponse(
    val memories: List<MemoryDto>
)

data class RememberRequest(
    val key: String,
    val value: String
)

data class HubAgentDto(
    val id: String,
    val name: String,
    val description: String,
    val status: String
)

data class HubAgentsResponse(
    val agents: List<HubAgentDto>
)

data class HubSessionRequest(
    val goal: String,
    @SerializedName("agent_ids") val agentIds: List<String>,
    val mode: String = "sequential"
)

data class HubSessionDto(
    val id: String,
    val goal: String,
    val status: String,
    @SerializedName("agent_ids") val agentIds: List<String>,
    @SerializedName("created_at") val createdAt: Long = 0L
)
