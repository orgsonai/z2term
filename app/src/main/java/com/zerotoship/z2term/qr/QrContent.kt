package com.zerotoship.z2term.qr

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Import only data. Scanning never starts a connection or executes a command. */
internal data class QrContent(
    val raw: String, val kind: Kind, val text: String = raw, val name: String = "",
    val host: String = "", val port: Int = 22, val user: String = "",
) {
    enum class Kind { TEXT, URL, SSH, COMMAND }
    companion object {
        const val MAX_TEXT = 4096
        fun singleLine(value: String): Boolean = value.isNotBlank() && value.length <= MAX_TEXT &&
            value.none { Character.isISOControl(it) || Character.getType(it) in setOf(
                Character.FORMAT.toInt(), Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt()) }

        fun parse(raw: String): QrContent {
            require(raw.isNotBlank() && raw.length <= MAX_TEXT)
            val uri = runCatching { URI(raw) }.getOrNull()
            if (uri?.scheme?.lowercase() in setOf("http", "https") && !uri?.host.isNullOrBlank() && uri?.rawUserInfo == null)
                return QrContent(raw, Kind.URL)
            if (uri?.scheme == "ssh") {
                require(uri.host != null && uri.rawQuery == null && uri.rawFragment == null)
                require(uri.path.isNullOrEmpty() || uri.path == "/")
                val user = uri.userInfo.orEmpty()
                require((user.isEmpty() || singleLine(user)) && !user.contains(':'))
                val port = if (uri.port == -1) 22 else uri.port
                require(port in 1..65535)
                return QrContent(raw, Kind.SSH, host = uri.host.removePrefix("[").removeSuffix("]"), port = port, user = user)
            }
            if (uri?.scheme == "z2term" && uri.host == "command") {
                require(uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null && uri.path.isNullOrEmpty())
                val fields = linkedMapOf<String, String>()
                for (part in uri.rawQuery.orEmpty().split('&')) {
                    val pair = part.split('=', limit = 2)
                    require(pair.size == 2 && pair[0] in setOf("name", "text") && !fields.containsKey(pair[0]))
                    fields[pair[0]] = URLDecoder.decode(pair[1], "UTF-8")
                }
                val text = fields["text"].orEmpty()
                val name = fields["name"].orEmpty()
                require(singleLine(text) && name.length <= 80 && (name.isEmpty() || singleLine(name)))
                return QrContent(raw, Kind.COMMAND, text = text, name = name)
            }
            return QrContent(raw, Kind.TEXT)
        }

        fun command(name: String, text: String): String {
            require(singleLine(text) && name.length <= 80 && (name.isEmpty() || singleLine(name)))
            val encoded = "z2term://command?name=" + URLEncoder.encode(name, "UTF-8") + "&text=" + URLEncoder.encode(text, "UTF-8")
            require(encoded.length <= MAX_TEXT)
            return encoded
        }

        fun ssh(host: String, port: Int, user: String): String {
            val uri = URI("ssh", user.takeIf { it.isNotEmpty() }, host.removePrefix("[").removeSuffix("]"), port, null, null, null).toASCIIString()
            parse(uri)
            return uri
        }
    }
}
