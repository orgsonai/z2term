package com.zerotoship.z2term.share

import java.net.URI

internal data class DirectShareConfig(val origin: String, val port: Int, val minutes: Int) {
    val tls get() = origin.startsWith("https://")
    val ipv6 get() = URI(origin).host.contains(':')
    companion object {
        fun sameOrigin(first: String, second: String): Boolean = runCatching {
            fun normalized(value: String): Triple<String, String, Int> {
                val uri = URI(parse(value, 8080, 15).origin)
                val host = uri.host
                // IPv6 spelling can differ after a browser canonicalizes a URL. Only literals use
                // InetAddress here, so untrusted Origin headers never trigger DNS requests.
                val key = if (host.contains(':')) java.net.InetAddress.getByName(host).address.joinToString(",")
                    else host
                return Triple(uri.scheme, key, if (uri.port == -1) (if (uri.scheme == "https") 443 else 80) else uri.port)
            }
            normalized(first) == normalized(second)
        }.getOrDefault(false)

        fun parse(value: String, port: Int, minutes: Int): DirectShareConfig {
            require(value.length <= 512 && port in 1024..65535 && minutes in setOf(5, 15, 60))
            val uri = URI(value.trim())
            val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT)
            require(scheme in setOf("http", "https") && !uri.host.isNullOrBlank())
            require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
            require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
            require(uri.port == -1 || uri.port in 1..65535)
            val host = uri.host.lowercase(java.util.Locale.ROOT)
            val publicPort = if (uri.port == (if (scheme == "https") 443 else 80)) -1 else uri.port
            return DirectShareConfig(scheme + "://" + host + (if (publicPort == -1) "" else ":" + publicPort), port, minutes)
        }
    }
}
