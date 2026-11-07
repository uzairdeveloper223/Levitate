package dev.uzair.levitate

import android.net.Uri

data class Pairing(val host: String, val port: Int, val token: String, val fingerprint: String, val name: String) {
    companion object {
        fun parse(text: String): Pairing {
            require(text.length <= 1024) { "This pairing link is too long." }
            val uri = Uri.parse(text.trim())
            require(uri.scheme == "levitate" && uri.host == "pair" && uri.getQueryParameter("v") == "1") {
                "Scan a Levitate QR code from your laptop." }
            val host = uri.getQueryParameter("host").orEmpty()
            require(isLocalAddress(host)) { "The pairing address must be on your local network." }
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 0
            require(port in 1024..65535) { "Invalid companion port." }
            val token = uri.getQueryParameter("token").orEmpty()
            val fingerprint = uri.getQueryParameter("fp").orEmpty().lowercase()
            require(token.matches(Regex("[a-fA-F0-9]{64}")) && fingerprint.matches(Regex("[a-f0-9]{64}"))) {
                "The pairing code is incomplete. Generate a new QR on the laptop." }
            return Pairing(host, port, token, fingerprint, uri.getQueryParameter("name").orEmpty().take(64))
        }

        private fun isLocalAddress(host: String): Boolean {
            val bytes = host.split('.').map { it.toIntOrNull() ?: return false }
            if (bytes.size != 4 || bytes.any { it !in 0..255 }) return false
            return bytes[0] == 10 || bytes[0] == 127 ||
                (bytes[0] == 192 && bytes[1] == 168) ||
                (bytes[0] == 172 && bytes[1] in 16..31) ||
                (bytes[0] == 169 && bytes[1] == 254)
        }
    }
}
