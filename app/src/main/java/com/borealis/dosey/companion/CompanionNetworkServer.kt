package com.borealis.dosey.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.security.auth.x500.X500Principal

/**
 * Minimal TLS-only endpoint for Companion bootstrap.
 *
 * Until pairing/authentication is implemented, only the non-sensitive status endpoint is served.
 * No medication data or write routes are exposed here.
 */
internal class CompanionNetworkServer(
    private val context: Context,
    private val onStateChanged: (Boolean, String?) -> Unit
) {
    // Bound concurrent clients and queued work so LAN clients cannot create unbounded threads.
    private val executor = ThreadPoolExecutor(
        2,
        4,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(16),
        ThreadPoolExecutor.AbortPolicy()
    )
    private var serverSocket: SSLServerSocket? = null
    private var nsdManager: NsdManager? = null
    private var registered = false
    private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var running = false

    @Synchronized
    fun start() {
        if (running) return
        running = true
        executor.execute {
            try {
                val sslContext = createServerSslContext()
                val socket = sslContext.serverSocketFactory.createServerSocket(0) as SSLServerSocket
                socket.enabledProtocols = socket.supportedProtocols.filter {
                    it == "TLSv1.3" || it == "TLSv1.2"
                }.toTypedArray()
                synchronized(this) {
                    if (!running) {
                        socket.close()
                        return@execute
                    }
                    serverSocket = socket
                }
                advertise(socket.localPort)
                onStateChanged(true, null)
                while (running) {
                    try {
                        val client = socket.accept()
                        try {
                            executor.execute { handleClient(client) }
                        } catch (_: java.util.concurrent.RejectedExecutionException) {
                            // Refuse excess connections rather than growing without bound.
                            try { client.close() } catch (_: Exception) { }
                        }
                    } catch (e: Exception) {
                        if (running) throw e
                    }
                }
            } catch (e: Exception) {
                stop()
                onStateChanged(false, e.message ?: "Unable to start the secure listener")
            }
        }
    }

    @Synchronized
    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
        val manager = nsdManager
        val listener = registrationListener
        if (manager != null && listener != null && registered) {
            try { manager.unregisterService(listener) } catch (_: Exception) { }
        }
        registrationListener = null
        registered = false
        executor.shutdownNow()
        onStateChanged(false, null)
    }

    private fun advertise(port: Int) {
        val manager = context.getSystemService(NsdManager::class.java) ?: return
        nsdManager = manager
        val info = NsdServiceInfo().apply {
            serviceName = "Dosey-${randomInstanceId()}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("protocol", "1")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                val wasStopped = synchronized(this@CompanionNetworkServer) {
                    if (running) {
                        registered = true
                        false
                    } else {
                        true
                    }
                }
                if (wasStopped) {
                    try { manager.unregisterService(this) } catch (_: Exception) { }
                }
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                synchronized(this@CompanionNetworkServer) { registered = false }
                if (running) onStateChanged(false, "Local network discovery failed ($errorCode)")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                synchronized(this@CompanionNetworkServer) { registered = false }
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onStateChanged(false, "Could not stop local network discovery ($errorCode)")
            }
        }
        registrationListener = listener
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun handleClient(socket: Socket) {
        socket.use { raw ->
            try {
                val client = raw as javax.net.ssl.SSLSocket
                client.soTimeout = 4000
                client.startHandshake()
                val input = client.inputStream
                val requestLine = readAsciiLine(input, MAX_REQUEST_LINE_BYTES) ?: return
                var headerBytes = 0
                var headerCount = 0
                var headersValid = true
                var hostHeaderSeen = false
                var hostHeaderCount = 0
                var contentLengthSeen = false
                var contentLength = 0
                var transferEncodingSeen = false
                var requestBodyDeclared = false
                while (headersValid) {
                    val line = readAsciiLine(input, MAX_HEADER_LINE_BYTES)
                    if (line == null) {
                        headersValid = false
                        break
                    }
                    headerBytes += line.length + 2
                    headerCount++
                    if (headerBytes > MAX_HEADER_BYTES || headerCount > MAX_HEADER_COUNT) {
                        headersValid = false
                        break
                    }
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator <= 0) {
                        headersValid = false
                        break
                    }
                    val rawName = line.substring(0, separator)
                    val name = rawName.lowercase(java.util.Locale.ROOT)
                    val value = line.substring(separator + 1).trim()
                    if (name.isEmpty() || rawName.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it != '-' }) {
                        headersValid = false
                        break
                    }
                    when (name) {
                        "host" -> {
                            hostHeaderCount++
                            hostHeaderSeen = value.isNotEmpty()
                        }
                        "content-length" -> {
                            if (contentLengthSeen) requestBodyDeclared = true
                            contentLengthSeen = true
                            val length = value.toIntOrNull()
                            if (length == null || length < 0 || length > MAX_PAIR_REQUEST_BODY_BYTES) {
                                requestBodyDeclared = true
                            } else {
                                contentLength = length
                            }
                        }
                        "transfer-encoding" -> {
                            transferEncodingSeen = true
                            requestBodyDeclared = true
                        }
                    }
                }
                val parts = requestLine.split(" ")
                val requestLineValid = parts.size == 3 &&
                    parts.none { it.isEmpty() } &&
                    parts[0].all { it in 'A'..'Z' || it in 'a'..'z' } &&
                    parts[1].startsWith("/") &&
                    parts[1].none { it <= ' ' || it.code == 127 } &&
                    (parts[2] == "HTTP/1.1" || parts[2] == "HTTP/1.0")
                val isPairRequest = requestLineValid &&
                    parts[0] == "POST" && parts[1] == "/v1/pair/request" && parts[2] == "HTTP/1.1"
                val headersSafe = headersValid && !transferEncodingSeen &&
                    hostHeaderCount <= 1 &&
                    (parts.size != 3 || parts[2] != "HTTP/1.1" || hostHeaderSeen) &&
                    (!requestBodyDeclared && (
                        (!isPairRequest && contentLength == 0) ||
                            (isPairRequest && contentLength in 1..MAX_PAIR_REQUEST_BODY_BYTES)
                        ))
                val isStatusRequest = headersSafe && requestLineValid &&
                    parts[0] == "GET" && parts[1] == "/v1/status" && parts[2] == "HTTP/1.1"
                val pairStatusRequest = headersSafe && requestLineValid &&
                    parts[0] == "GET" && parts[1].startsWith("/v1/pair/status?id=") &&
                    parts[2] == "HTTP/1.1"
                val pairStatusId = if (pairStatusRequest) {
                    parts[1].substringAfter("/v1/pair/status?id=").takeIf {
                        it.matches(Regex("[a-fA-F0-9-]{36}"))
                    }
                } else null
                val pairRequestBody = if (headersSafe && isPairRequest) {
                    val bytes = ByteArray(contentLength)
                    var offset = 0
                    while (offset < bytes.size) {
                        val count = input.read(bytes, offset, bytes.size - offset)
                        if (count < 0) throw java.io.IOException("Incomplete pairing request body")
                        offset += count
                    }
                    try {
                        JSONObject(bytes.toString(Charsets.UTF_8))
                    } catch (_: org.json.JSONException) {
                        null
                    }
                } else null
                val clientNameValue = pairRequestBody?.opt("clientName")
                val pairRequestInputValid = pairRequestBody != null &&
                    pairRequestBody.length() == 1 &&
                    clientNameValue is String &&
                    clientNameValue.isNotBlank() &&
                    clientNameValue.length <= 80
                val pairingRequest = if (pairRequestInputValid) {
                    CompanionPairingManager.createRequest(clientNameValue as String)
                } else null
                val pairRequestValid = isPairRequest && pairRequestInputValid && pairingRequest != null
                val body = when {
                    !headersSafe || !requestLineValid -> JSONObject().put("error", "bad_request").toString()
                    isStatusRequest -> JSONObject()
                        .put("service", "Dosey Companion")
                        .put("protocolVersion", 1)
                        .put("status", if (CompanionPairingManager.pendingRequest.value == null) "pairing_required" else "pairing_pending")
                        .put("medicationDataAvailable", false)
                        .put("certificateSha256", certificateFingerprint.value.orEmpty())
                        .put("serverTime", System.currentTimeMillis())
                        .toString()
                    pairStatusRequest -> JSONObject()
                        .put("requestId", pairStatusId ?: "")
                        .put("status", pairStatusId?.let(CompanionPairingManager::status) ?: "not_found")
                        .put("credentialsIssued", false)
                        .toString()
                    pairRequestValid -> JSONObject()
                        .put("requestId", pairingRequest!!.id)
                        .put("verificationCode", pairingRequest.verificationCode)
                        .put("expiresAt", pairingRequest.expiresAt)
                        .put("status", "awaiting_android_approval")
                        .toString()
                    isPairRequest -> JSONObject().put(
                        "error",
                        when {
                            !pairRequestInputValid -> "invalid_pairing_request"
                            else -> "pairing_request_busy_or_rate_limited"
                        }
                    ).toString()
                    else -> JSONObject().put("error", "not_found").toString()
                }
                val status = when {
                    !headersSafe || !requestLineValid -> "400 Bad Request"
                    isStatusRequest || pairStatusRequest || pairRequestValid -> "200 OK"
                    isPairRequest && !pairRequestInputValid -> "400 Bad Request"
                    isPairRequest -> "409 Conflict"
                    else -> "404 Not Found"
                }
                val payload = body.toByteArray(Charsets.UTF_8)
                val response = "HTTP/1.1 $status\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${payload.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n"
                val output = client.outputStream
                output.write(response.toByteArray(Charsets.US_ASCII))
                output.write(payload)
                output.flush()
            } catch (_: Exception) {
                // Invalid or interrupted clients are intentionally ignored.
            }
        }
    }

    private fun readAsciiLine(input: java.io.InputStream, maxLength: Int): String? {
        val line = StringBuilder()
        while (line.length <= maxLength) {
            val value = input.read()
            if (value == -1) return if (line.isEmpty()) null else line.toString()
            if (value == '\n'.code) {
                if (line.isNotEmpty() && line.last() == '\r') line.setLength(line.length - 1)
                return line.toString()
            }
            line.append(value.toChar())
        }
        throw java.io.IOException("HTTP line exceeds allowed length")
    }

    private fun createServerSslContext(): SSLContext {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )
            val now = System.currentTimeMillis()
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN
            )
                .setKeySize(2048)
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA384,
                    KeyProperties.DIGEST_SHA512
                )
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=Dosey Companion"))
                .setCertificateSerialNumber(
                    BigInteger.valueOf(SecureRandom().nextLong().ushr(1).coerceAtLeast(1L))
                )
                .setCertificateNotBefore(Date(now - 60_000L))
                .setCertificateNotAfter(Date(now + CERT_VALIDITY_MS))
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
        val certificate = keyStore.getCertificate(KEY_ALIAS)
            ?: throw IllegalStateException("Companion TLS certificate is missing")
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }
        mutableCertificateFingerprint.value = fingerprint

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, null)
        return SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, null, SecureRandom())
        }
    }

    private fun randomInstanceId(): String = buildString {
        val random = SecureRandom()
        repeat(6) { append("0123456789abcdef"[random.nextInt(16)]) }
    }

    companion object {
        private val mutableCertificateFingerprint = MutableStateFlow<String?>(null)
        val certificateFingerprint: StateFlow<String?> = mutableCertificateFingerprint.asStateFlow()

        private const val SERVICE_TYPE = "_dosey._tcp."
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "dosey_companion_tls"
        private const val CERT_VALIDITY_MS = 10L * 365 * 24 * 60 * 60 * 1000
        private const val MAX_REQUEST_LINE_BYTES = 2048
        private const val MAX_HEADER_LINE_BYTES = 2048
        private const val MAX_HEADER_BYTES = 8192
        private const val MAX_HEADER_COUNT = 64
        private const val MAX_PAIR_REQUEST_BODY_BYTES = 1024
    }
}
