package com.god.notifypush

import kotlinx.serialization.Serializable

@Serializable
enum class DispatchStatus {
    DELIVERED,
    REJECTED,
    FAILED,
}

@Serializable
data class DispatchResult(
    val destinationId: String,
    val destinationName: String,
    val status: DispatchStatus,
    val httpCode: Int? = null,
    val error: String? = null,
)
