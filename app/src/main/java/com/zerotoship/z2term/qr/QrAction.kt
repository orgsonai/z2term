package com.zerotoship.z2term.qr

import java.net.URI
import java.net.URLDecoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What a scanned QR can open in another app (0.8.602). Parsing is pure; [QrActionLauncher] builds intents.
 *
 * Nothing opens by itself: the review screen shows one button for the parsed action (user decision).
 * z2term commands and SSH endpoints are not actions; they keep their in-app review ([QrContent]).
 */
internal sealed interface QrAction {
    data class Web(val uri: String, val host: String) : QrAction
    data class Link(val uri: String, val scheme: String) : QrAction
    data class Dial(val number: String) : QrAction
    data class Email(val to: String, val subject: String = "", val body: String = "") : QrAction
    data class Sms(val number: String, val body: String = "") : QrAction
    data class Wifi(val ssid: String, val security: String, val password: String, val hidden: Boolean) : QrAction
    data class Contact(
        val name: String, val phones: List<String>, val emails: List<String>,
        val organization: String, val title: String, val address: String, val note: String,
    ) : QrAction
    data class Event(
        val title: String, val location: String, val description: String,
        val begin: Long?, val end: Long?, val allDay: Boolean,
    ) : QrAction

    companion object {
        private val BLOCKED = setOf("javascript", "vbscript", "file", "content", "data", "about", "blob",
            "intent", "android-app", "jar", "z2term", "ssh")
        /** Link schemes normally written without `//`. Other `word:value` text stays plain text. */
        private val OPAQUE = setOf("geo", "bitcoin", "ethereum", "litecoin", "magnet", "sip", "sips", "skype",
            "spotify", "callto", "maps", "xmpp")
        private val SCHEME = Regex("([A-Za-z][A-Za-z0-9+.-]*):(.*)", RegexOption.DOT_MATCHES_ALL)
        private val PHONE = Regex("[0-9+*#(),;. /pPwW-]{1,64}")
        private val BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val BASIC_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

        fun parse(raw: String, zone: ZoneId = ZoneId.systemDefault()): QrAction? =
            runCatching { parseOrNull(raw.trim(), zone) }.getOrNull()

        private fun parseOrNull(raw: String, zone: ZoneId): QrAction? {
            if (raw.isEmpty() || raw.length > QrContent.MAX_TEXT) return null
            val head = raw.take(16).uppercase(Locale.ROOT)
            if (head.startsWith("WIFI:")) return wifi(fields(raw.substring(5)))
            if (head.startsWith("MATMSG:")) {
                val f = fields(raw.substring(7))
                return Email(f.first("TO"), f.first("SUB"), f.first("BODY")).takeIf { it.to.isNotBlank() }
            }
            if (head.startsWith("MECARD:")) return mecard(fields(raw.substring(7)))
            if (head.startsWith("BEGIN:VCARD")) return vcard(properties(raw))
            if (head.startsWith("BEGIN:VCALENDAR") || head.startsWith("BEGIN:VEVENT")) return event(properties(raw), zone)
            if (head.startsWith("SMSTO:") || head.startsWith("MMSTO:")) {
                // ZXing form SMSTO:number:message; the message may contain spaces and colons.
                val rest = raw.substringAfter(':')
                if ('?' !in rest && ':' in rest) {
                    val (number, body) = rest.split(':', limit = 2)
                    return Sms(number.trim(), body).takeIf { PHONE.matches(it.number) }
                }
            }
            if (raw.any { it.isWhitespace() || Character.isISOControl(it) }) return null
            val match = SCHEME.matchEntire(raw) ?: return null
            val scheme = match.groupValues[1].lowercase(Locale.ROOT)
            val rest = match.groupValues[2]
            if (rest.isEmpty() || scheme in BLOCKED) return null
            return when (scheme) {
                "http", "https" -> URI(raw).takeIf { !it.host.isNullOrBlank() && it.rawUserInfo == null }
                    ?.let { Web(raw, it.host) }
                "tel" -> decode(rest).takeIf { PHONE.matches(it) }?.let { Dial(it) }
                "mailto" -> {
                    val parts = rest.split('?', limit = 2)
                    val query = query(parts.getOrElse(1) { "" })
                    Email(decode(parts[0]), query["subject"].orEmpty(), query["body"].orEmpty())
                        .takeIf { it.to.isNotBlank() || it.subject.isNotBlank() || it.body.isNotBlank() }
                }
                "sms", "smsto", "mms", "mmsto" -> {
                    val parts = rest.split('?', limit = 2)
                    Sms(decode(parts[0]), query(parts.getOrElse(1) { "" })["body"].orEmpty())
                        .takeIf { PHONE.matches(it.number) }
                }
                else -> if (rest.startsWith("//") || scheme in OPAQUE) { URI(raw); Link(raw, scheme) } else null
            }
        }

        /** Percent-decoding that keeps `+` (mailto/sms URIs encode spaces as %20). */
        private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")

        private fun query(value: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for (part in value.split('&')) {
                if (part.isEmpty()) continue
                val pair = part.split('=', limit = 2)
                out.putIfAbsent(decode(pair[0]).lowercase(Locale.ROOT), decode(pair.getOrElse(1) { "" }))
            }
            return out
        }

        /** `KEY:value;` pairs of WIFI:, MATMSG: and MECARD:, keeping repeated keys and resolving `\` escapes. */
        private fun fields(body: String): List<Pair<String, String>> {
            val out = ArrayList<Pair<String, String>>()
            val current = StringBuilder()
            var key: String? = null
            var i = 0
            while (i < body.length) {
                val c = body[i]
                when {
                    c == '\\' && i + 1 < body.length -> { current.append(body[i + 1]); i++ }
                    c == ':' && key == null -> { key = current.toString().trim().uppercase(Locale.ROOT); current.clear() }
                    c == ';' -> { key?.let { out += it to current.toString() }; key = null; current.clear() }
                    else -> current.append(c)
                }
                i++
            }
            key?.let { out += it to current.toString() }
            return out
        }

        private fun List<Pair<String, String>>.first(key: String) = firstOrNull { it.first == key }?.second.orEmpty()
        private fun List<Pair<String, String>>.all(key: String) =
            filter { it.first == key }.map { it.second.trim() }.filter { it.isNotEmpty() }

        private fun unquote(value: String) =
            if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) value.substring(1, value.length - 1) else value

        private fun wifi(f: List<Pair<String, String>>): Wifi? {
            val ssid = unquote(f.first("S"))
            if (ssid.isEmpty()) return null
            val password = unquote(f.first("P"))
            val security = f.first("T").trim().ifEmpty { if (password.isEmpty()) "nopass" else "WPA" }
            return Wifi(ssid, security, password, f.first("H").trim().equals("true", ignoreCase = true))
        }

        private fun mecard(f: List<Pair<String, String>>): Contact? {
            val n = f.first("N").split(',', limit = 2)
            return contact(personName(n[0].trim(), n.getOrElse(1) { "" }.trim()), f.all("TEL"), f.all("EMAIL"),
                f.first("ORG").trim(), "", f.first("ADR").trim(), listOf(f.first("NOTE"), f.first("URL")))
        }

        private fun contact(name: String, phones: List<String>, emails: List<String>, organization: String,
                            title: String, address: String, notes: List<String>): Contact? =
            Contact(name, phones.take(3), emails.take(3), organization, title, address,
                notes.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"))
                .takeIf { it.name.isNotBlank() || it.phones.isNotEmpty() || it.emails.isNotEmpty() }

        /** Japanese/Chinese/Korean names keep family-name order. */
        private fun personName(family: String, given: String): String {
            val scripts = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL)
            val eastAsian = (family + given).any { Character.UnicodeScript.of(it.code) in scripts }
            return (if (eastAsian) listOf(family, given) else listOf(given, family))
                .filter { it.isNotBlank() }.joinToString(" ")
        }

        private data class Property(val name: String, val params: Map<String, String>, val value: String)

        /** Unfolded `NAME;PARAMS:value` lines of vCard / iCalendar. Names are upper-case, groups removed. */
        private fun properties(raw: String): List<Property> {
            val unfolded = raw.replace("\r\n", "\n").replace('\r', '\n').replace(Regex("\n[ \t]"), "")
            return unfolded.split('\n').mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) return@mapNotNull null
                val head = line.substring(0, colon).split(';')
                Property(
                    head[0].substringAfterLast('.').trim().uppercase(Locale.ROOT),
                    head.drop(1).associate { it.substringBefore('=').trim().uppercase(Locale.ROOT) to it.substringAfter('=', "") },
                    line.substring(colon + 1),
                )
            }
        }

        /** Components separated by unescaped `;`, each unescaped. */
        private fun components(value: String): List<String> {
            val parts = ArrayList<String>()
            val current = StringBuilder()
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    current.append(c).append(value[i + 1]); i += 2; continue
                }
                if (c == ';') { parts += unescape(current.toString()); current.clear() } else current.append(c)
                i++
            }
            parts += unescape(current.toString())
            return parts
        }

        private fun unescape(value: String): String {
            val out = StringBuilder()
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    val next = value[i + 1]
                    out.append(if (next == 'n' || next == 'N') '\n' else next)
                    i += 2
                } else {
                    out.append(c); i++
                }
            }
            return out.toString()
        }

        private fun vcard(props: List<Property>): Contact? {
            fun one(name: String) = props.firstOrNull { it.name == name }?.value.orEmpty()
            fun many(name: String) = props.filter { it.name == name }.map { unescape(it.value).trim() }.filter { it.isNotEmpty() }
            val n = components(one("N"))
            val name = unescape(one("FN")).trim()
                .ifEmpty { personName(n.getOrElse(0) { "" }.trim(), n.getOrElse(1) { "" }.trim()) }
            val address = components(one("ADR")).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            return contact(name, many("TEL"), many("EMAIL"), components(one("ORG")).first().trim(),
                unescape(one("TITLE")).trim(), address, listOf(unescape(one("NOTE")), unescape(one("URL"))))
        }

        private fun event(props: List<Property>, zone: ZoneId): Event? {
            // Only the first VEVENT; calendar files may carry other components before it.
            val start = props.indexOfFirst { it.name == "BEGIN" && it.value.trim().equals("VEVENT", ignoreCase = true) }
            val body = if (start < 0) props else props.drop(start + 1)
                .takeWhile { !(it.name == "END" && it.value.trim().equals("VEVENT", ignoreCase = true)) }
            fun text(name: String) = body.firstOrNull { it.name == name }?.let { unescape(it.value).trim() }.orEmpty()
            val begin = body.firstOrNull { it.name == "DTSTART" }?.let { time(it, zone) }
            val end = body.firstOrNull { it.name == "DTEND" }?.let { time(it, zone) }
            val title = text("SUMMARY")
            if (title.isEmpty() && begin == null) return null
            return Event(title, text("LOCATION"), text("DESCRIPTION"), begin?.first, end?.first, begin?.second == true)
        }

        /** Epoch millis and whether the value is a date. Dates use UTC midnight, as calendar inserts expect. */
        private fun time(property: Property, zone: ZoneId): Pair<Long, Boolean>? = runCatching {
            val value = property.value.trim()
            when {
                property.params["VALUE"].equals("DATE", ignoreCase = true) || value.matches(Regex("\\d{8}")) ->
                    LocalDate.parse(value, BASIC_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() to true
                value.endsWith("Z") ->
                    LocalDateTime.parse(value.dropLast(1), BASIC_TIME).toInstant(ZoneOffset.UTC).toEpochMilli() to false
                else -> {
                    val tz = property.params["TZID"]?.let { runCatching { ZoneId.of(it.trim('"')) }.getOrNull() } ?: zone
                    LocalDateTime.parse(value, BASIC_TIME).atZone(tz).toInstant().toEpochMilli() to false
                }
            }
        }.getOrNull()
    }
}
