package com.borealis.dosey.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.security.KeyPairGeneratorSpec
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private var serverSocket: SSLServerSocket? = null
    private var nsdManager: NsdManager? = null
    private var registered = false
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
                        executor.execute { handleClient(client) }
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
        if (manager != null && registered) {
            try {
                manager.unregisterService(object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) { registered = false }
                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { registered = false }
                })
            } catch (_: Exception) { }
        }
        registered = false
        onStateChanged(false, null)
    }

    private fun advertise(port: Int) {
        val manager = context.getSystemService(NsdManager::class.java) ?: return
        nsdManager = manager
        val info = NsdServiceInfo().apply {
            serviceName = "Dosey-\${randomInstanceId()}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("protocol", "1")
        }
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) { registered = true }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onStateChanged(false, "Local network discovery failed ($errorCode)")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) { registered = false }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onStateChanged(false, "Could not stop local network discovery ($errorCode)")
            }
        })
    }

    private fun handleClient(socket: Socket) {
        socket.use { raw ->
            try {
                val client = raw as javax.net.ssl.SSLSocket
                client.soTimeout = 4000
                client.startHandshake()
                val reader = BufferedReader(InputStreamReader(client.inputStream, Charsets.US_ASCII))
                val requestLine = reader.readLine() ?: return
                var bytes = requestLine.length
                while (bytes < 8192) {
                    val line = reader.readLine() ?: break
                    bytes += line.length
                    if (line.isEmpty()) break
                }
                val parts = requestLine.split(" ")
                val isStatusRequest = parts.size >= 2 && parts[0] == "GET" && parts[1] == "/v1/status"
                val body = if (isStatusRequest) {
                    JSONObject()
                        .put("service", "Dosey Companion")
                        .put("protocolVersion", 1)
                        .put("status", "pairing_required")
                        .put("medicationDataAvailable", false)
                        .put("serverTime", System.currentTimeMillis())
                        .toString()
                } else JSONObject().put("error", "not_found").toString()
                val status = if (isStatusRequest) "200 OK" else "404 Not Found"
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

    private fun createServerSslContext(): SSLContext {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE)
            val spec = KeyPairGeneratorSpec.Builder(context)
                .setAlias(KEY_ALIAS)
                .setSubject(X500Principal("CN=Dosey Companion"))
                .setSerialNumber(BigInteger.valueOf(SecureRandom().nextLong().ushr(1).coerceAtLeast(1L)))
                .setStartDate(Date(System.currentTimeMillis() - 60_000L))
                .setEndDate(Date(System.currentTimeMillis() + CERT_VALIDITY_MS))
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
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
        private const val SERVICE_TYPE = "_dosey._tcp."
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "dosey_companion_tls"
        private const val CERT_VALIDITY_MS = 10L * 365 * 24 * 60 * 60 * 1000
    }
}
