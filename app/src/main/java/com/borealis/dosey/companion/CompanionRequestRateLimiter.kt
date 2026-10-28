package com.borealis.dosey.companion

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Small in-memory per-client limiter. Credentials are hashed before becoming map keys.
 * Limits reset when the opt-in Companion service process is restarted.
 */
internal object CompanionRequestRateLimiter {
    private data class Window(var startedAt: Long, var count: Int)

    private val reads = ConcurrentHashMap<String, Window>()
    private val writes = ConcurrentHashMap<String, Window>()

    fun allow(token: String, write: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val key = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val windows = if (write) writes else reads
        val limit = if (write) MAX_WRITES_PER_MINUTE else MAX_READS_PER_MINUTE
        val window = windows.computeIfAbsent(key) { Window(now, 0) }
        synchronized(window) {
            if (now < window.startedAt || now - window.startedAt >= WINDOW_MS) {
                window.startedAt = now
                window.count = 0
            }
            if (window.count >= limit) return false
            window.count++
        }
        if (reads.size + writes.size > MAX_TRACKED_BUCKETS) {
            val cutoff = now - WINDOW_MS
            reads.entries.removeIf { it.value.startedAt < cutoff }
            writes.entries.removeIf { it.value.startedAt < cutoff }
        }
        return true
    }

    private const val WINDOW_MS = 60_000L
    private const val MAX_READS_PER_MINUTE = 120
    private const val MAX_WRITES_PER_MINUTE = 30
    private const val MAX_TRACKED_BUCKETS = 256
}
