package dev.uzair.levitate

import android.os.Build
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

data class ConnectionStatus(val connected: Boolean, val message: String, val latencyMs: Long = 0,
                            val inputReady: Boolean = true, val desktopPaused: Boolean = false)

class RemoteConnection(private val onStatus: (ConnectionStatus) -> Unit) {
    @Volatile private var queue = ArrayBlockingQueue<String>(128)
    @Volatile private var socket: Socket? = null
    @Volatile private var generation = 0
    @Volatile var connected = false
        private set

    @Synchronized fun connect(pairing: Pairing) {
        disconnect()
        val session = generation
        onStatus(ConnectionStatus(false, "Connecting to ${pairing.name.ifBlank { pairing.host }}…"))
        Thread({ openSession(pairing, session) }, "levitate-connect").start()
    }

    private fun openSession(pairing: Pairing, session: Int) {
        try {
            val raw = Socket()
            synchronized(this) {
                if (generation != session) { raw.close(); return }
                socket = raw
            }
            raw.connect(InetSocketAddress(pairing.host, pairing.port), 5000)
            raw.tcpNoDelay = true
            raw.soTimeout = 5000
            val context = pinnedContext(pairing.fingerprint)
            val secure = context.socketFactory.createSocket(raw, pairing.host, pairing.port, true) as SSLSocket
            secure.enabledProtocols = secure.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
            synchronized(this) {
                if (generation != session) { secure.close(); return }
                socket = secure
            }
            secure.startHandshake()
            authenticate(secure, pairing)
            val reader = BufferedReader(InputStreamReader(secure.inputStream, Charsets.UTF_8))
            val welcome = JSONObject(readBoundedLine(reader))
            check(welcome.optString("type") == "welcome" && welcome.opt("v") is Int && welcome.getInt("v") == 1 &&
                welcome.opt("input_ready") is Boolean) { "Invalid companion welcome" }
            synchronized(this) {
                if (generation != session) return
                connected = true
            }
            onStatus(ConnectionStatus(true, pairing.name.ifBlank { pairing.host },
                inputReady = welcome.getBoolean("input_ready")))
            val outgoing = queue
            Thread({ writeCommands(secure, session, outgoing) }, "levitate-send").start()
            receiveStatus(reader, session, pairing)
        } catch (error: Exception) {
            if (generation == session) onStatus(ConnectionStatus(false, connectionError(error)))
        } finally {
            synchronized(this) {
                if (generation == session) {
                    connected = false
                    try { socket?.close() } catch (_: Exception) { }
                    socket = null
                    queue.clear()
                }
            }
        }
    }

    private fun authenticate(secure: SSLSocket, pairing: Pairing) {
        val hello = JSONObject().put("type", "hello").put("v", 1).put("token", pairing.token)
            .put("name", Build.MODEL.take(64))
        secure.outputStream.write((hello.toString() + "\n").toByteArray(Charsets.UTF_8))
    }

    private fun writeCommands(secure: SSLSocket, session: Int, outgoing: ArrayBlockingQueue<String>) {
        try {
            var nextPing = 0L
            while (generation == session && !secure.isClosed) {
                val wait = (nextPing - System.nanoTime() / 1_000_000).coerceIn(1, 1000)
                val message = outgoing.poll(wait, TimeUnit.MILLISECONDS)
                if (generation != session) break
                if (message != null) secure.outputStream.write(message.toByteArray(Charsets.UTF_8))
                val now = System.nanoTime() / 1_000_000
                if (now >= nextPing) {
                    val ping = JSONObject().put("type", "ping").put("id", now)
                    secure.outputStream.write((ping.toString() + "\n").toByteArray(Charsets.UTF_8))
                    nextPing = now + 1000
                }
            }
        } catch (_: Exception) {
            try { secure.close() } catch (_: Exception) { }
        }
    }

    private fun receiveStatus(reader: BufferedReader, session: Int, pairing: Pairing) {
        while (generation == session) {
            val message = JSONObject(readBoundedLine(reader))
            check(message.optString("type") == "pong" && message.opt("id") is Number &&
                message.opt("input_ready") is Boolean && message.opt("paused") is Boolean) {
                "Invalid companion response"
            }
            val latency = (System.nanoTime() / 1_000_000 - message.getLong("id")).coerceIn(0, 9999)
            onStatus(ConnectionStatus(true, pairing.name.ifBlank { pairing.host }, latency,
                message.optBoolean("input_ready", false), message.optBoolean("paused", false)))
        }
    }

    private fun readBoundedLine(reader: BufferedReader): String {
        val line = StringBuilder()
        while (line.length <= 2048) {
            val character = reader.read()
            check(character != -1) { "Companion disconnected" }
            if (character == 10) return line.toString()
            line.append(character.toChar())
        }
        error("Invalid companion response")
    }

    fun send(message: JSONObject) {
        if (!connected) return
        if (!queue.offer(message.toString() + "\n")) {
            // Never discard a button-up behind stale motion: close and let Linux release all keys.
            disconnect()
            onStatus(ConnectionStatus(false, "Connection fell behind. Tap Reconnect."))
        }
    }

    @Synchronized fun disconnect() {
        generation++
        connected = false
        queue.clear()
        queue = ArrayBlockingQueue(128)
        try { socket?.close() } catch (_: Exception) { }
        socket = null
    }

    private fun pinnedContext(fingerprint: String): SSLContext {
        val expected = fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                throw CertificateException("Client certificates are unsupported")
            }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty()) throw CertificateException("Missing certificate")
                chain[0].checkValidity()
                val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
                if (!MessageDigest.isEqual(expected, actual)) throw CertificateException("Laptop certificate changed")
            }
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    }

    private fun connectionError(error: Exception): String = when (error) {
        is javax.net.ssl.SSLException -> "Could not verify this laptop. Scan a fresh QR code."
        is java.net.SocketTimeoutException -> "Laptop did not respond. Check Wi-Fi and the firewall."
        is java.net.ConnectException -> "Companion is unavailable. Open Levitate on your laptop."
        else -> "Disconnected. Check the companion and tap Reconnect."
    }
}
