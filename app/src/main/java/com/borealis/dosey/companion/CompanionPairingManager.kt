package com.borealis.dosey.companion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID

/**
 * In-memory pairing approval state. Requests expire quickly and are discarded when the
 * service process stops. Credentials and data routes are intentionally not issued here yet.
 */
internal object CompanionPairingManager {
    data class Request(
        val id: String,
        val clientName: String,
        val verificationCode: String,
        val expiresAt: Long
    )

    private val random = SecureRandom()
    private val mutablePendingRequest = MutableStateFlow<Request?>(null)
    val pendingRequest: StateFlow<Request?> = mutablePendingRequest.asStateFlow()

    @Synchronized
    fun createRequest(clientName: String): Request? {
        val now = System.currentTimeMillis()
        val current = mutablePendingRequest.value
        if (current != null && current.expiresAt > now) return null

        val code = (100_000 + random.nextInt(900_000)).toString()
        return Request(
            id = UUID.randomUUID().toString(),
            clientName = clientName.trim().take(80).ifBlank { "Unknown PC" },
            verificationCode = code,
            expiresAt = now + REQUEST_LIFETIME_MS
        ).also { mutablePendingRequest.value = it }
    }

    @Synchronized
    fun approve(requestId: String): Boolean {
        val request = mutablePendingRequest.value ?: return false
        if (request.id != requestId || request.expiresAt <= System.currentTimeMillis()) {
            mutablePendingRequest.value = null
            return false
        }
        mutablePendingRequest.value = null
        return true
    }

    @Synchronized
    fun reject(requestId: String): Boolean {
        val request = mutablePendingRequest.value ?: return false
        if (request.id != requestId) return false
        mutablePendingRequest.value = null
        return true
    }

    @Synchronized
    fun clearExpired() {
        val request = mutablePendingRequest.value
        if (request != null && request.expiresAt <= System.currentTimeMillis()) {
            mutablePendingRequest.value = null
        }
    }

    const val REQUEST_LIFETIME_MS = 120_000L
}
