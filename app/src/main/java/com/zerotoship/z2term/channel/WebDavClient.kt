package com.zerotoship.z2term.channel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import com.zerotoship.z2term.net.HostAddress
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** WebDAV を共通ファイル画面へ載せるクライアント。TLS 証明書は OkHttp の既定検証に従う。 */
class WebDavClient private constructor(
    private val baseUrl: HttpUrl,
    private val user: String,
    private val password: String,
    private val dns: Dns? = null,
    private val hostHeader: String? = null,
) : RemoteFs {
    private val http = OkHttpClient.Builder().apply {
        dns?.let { dns(it) }
    }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override val home: String = "/"
    @Volatile
    override var isAlive: Boolean = true
        private set

    override suspend fun list(path: String): List<SftpEntry> = withContext(Dispatchers.IO) {
        val target = urlFor(path)
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            |<d:propfind xmlns:d="DAV:"><d:prop>
            |<d:resourcetype/><d:getcontentlength/><d:getlastmodified/>
            |</d:prop></d:propfind>
        """.trimMargin()
        val request = request(target)
            .header("Depth", "1")
            .method("PROPFIND", xml.toRequestBody(XML))
            .build()
        execute(request).use { response ->
            requireCode(response, 207, 200)
            val body = response.body.string()
            val entries = parseMultiStatus(body, target, path).toMutableList()
            if (path != "/") {
                entries.add(0, SftpEntry("..", true, false, 0, 0, ""))
            }
            entries
        }
    }

    override suspend fun download(remotePath: String, sink: OutputStream) = withContext(Dispatchers.IO) {
        execute(request(urlFor(remotePath)).get().build()).use { response ->
            requireCode(response, 200, 206)
            response.body.byteStream().use { it.copyTo(sink) }
        }
        Unit
    }

    override suspend fun upload(source: InputStream, remotePath: String) = withContext(Dispatchers.IO) {
        val body = object : RequestBody() {
            override fun contentType(): MediaType? = null
            override fun writeTo(sink: BufferedSink) {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    sink.write(buffer, 0, count)
                }
            }
        }
        execute(request(urlFor(remotePath)).put(body).build()).use {
            requireCode(it, 200, 201, 204)
        }
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        execute(request(urlFor(path)).method("MKCOL", EMPTY).build()).use {
            requireCode(it, 201, 204)
        }
    }

    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
        val destination = urlFor(to).toString()
        execute(
            request(urlFor(from))
                .header("Destination", destination)
                .header("Overwrite", "T")
                .method("MOVE", EMPTY)
                .build()
        ).use { requireCode(it, 201, 204) }
    }

    override suspend fun rm(path: String) = delete(path)
    override suspend fun rmdir(path: String) {
        check(list(path).none { it.name != ".." }) { "Directory is not empty" }
        delete(path)
    }

    private suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        execute(request(urlFor(path)).delete().build()).use {
            requireCode(it, 200, 202, 204)
        }
    }

    override fun close() {
        isAlive = false
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private fun request(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "*/*")
        .apply {
            hostHeader?.let { header("Host", it) }
            if (user.isNotBlank()) header("Authorization", Credentials.basic(user, password))
        }

    private fun execute(request: Request): Response = try {
        http.newCall(request).execute()
    } catch (e: Throwable) {
        isAlive = false
        throw e
    }

    private fun requireCode(response: Response, vararg expected: Int) {
        if (response.code in expected) return
        val detail = runCatching { response.body.string().take(512) }.getOrDefault("")
        error("HTTP ${response.code} ${response.message}${if (detail.isBlank()) "" else ": $detail"}")
    }

    private fun urlFor(path: String): HttpUrl {
        val builder = baseUrl.newBuilder()
        RemotePath.segments(path).forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun parseMultiStatus(xml: String, requestUrl: HttpUrl, requestedPath: String): List<SftpEntry> {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(xml.reader())
        }
        val out = ArrayList<SftpEntry>()
        var href: String? = null
        var directory = false
        var size = 0L
        var modified = 0L
        var inResponse = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            val local = parser.name?.lowercase()
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (local) {
                    "response" -> {
                        inResponse = true
                        href = null
                        directory = false
                        size = 0
                        modified = 0
                    }
                    "href" -> if (inResponse) href = parser.nextText()
                    "collection" -> if (inResponse) directory = true
                    "getcontentlength" -> if (inResponse) size = parser.nextText().trim().toLongOrNull() ?: 0
                    "getlastmodified" -> if (inResponse) modified = parseHttpDate(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (local == "response" && inResponse) {
                    entryFor(href, requestUrl, requestedPath, directory, size, modified)?.let(out::add)
                    inResponse = false
                }
            }
            parser.next()
        }
        return out.sortedWith(compareByDescending<SftpEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    private fun entryFor(
        href: String?,
        requestUrl: HttpUrl,
        requestedPath: String,
        directory: Boolean,
        size: Long,
        modified: Long
    ): SftpEntry? {
        val resolved = href?.let { requestUrl.resolve(it) } ?: return null
        val root = baseUrl.pathSegments.filter { it.isNotBlank() }
        val all = resolved.pathSegments.filter { it.isNotBlank() }
        if (all.take(root.size) != root) return null
        val relative = all.drop(root.size)
        val requested = RemotePath.segments(requestedPath)
        if (relative == requested || relative.size != requested.size + 1) return null
        if (relative.take(requested.size) != requested) return null
        return SftpEntry(relative.last(), directory, false, size, modified, "")
    }

    private fun parseHttpDate(value: String): Long = runCatching {
        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
    }.getOrDefault(0)

    companion object {
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val EMPTY = ByteArray(0).toRequestBody(null)

        suspend fun connect(profile: SshProfile): WebDavClient = withContext(Dispatchers.IO) {
            require(profile.host.startsWith("http://") || profile.host.startsWith("https://")) {
                "WebDAV URL must start with http:// or https://"
            }
            val normalized = profile.host.trim().let { if (it.endsWith('/')) it else "$it/" }
            WebDavClient(normalized.toHttpUrl(), profile.user, profile.password)
        }

        /** SSH 転送時も URL のホスト名を保ち、HTTPS の SNI / 証明書検証を壊さない。 */
        suspend fun connect(
            service: RemoteService,
            routeHost: String,
            routePort: Int,
        ): WebDavClient = withContext(Dispatchers.IO) {
            val serviceHost = HostAddress.normalize(service.host)
            require(serviceHost.isNotBlank()) { "WebDAV host is required" }
            val scheme = if (service.webDavHttps) "https" else "http"
            val base = HttpUrl.Builder()
                .scheme(scheme)
                .host(serviceHost)
                .port(routePort)
                .apply {
                    RemotePath.segments(service.path).forEach(::addPathSegment)
                    addPathSegment("")
                }
                .build()
            // HttpUrl は IPv6 を正規形へ畳むことがある。入力文字列ではなく、実際に Request が
            // Dns へ渡す canonical host と比べないと SSH 転送を迂回してしまう。
            val canonicalHost = base.host
            val tunneled = service.useSshTunnel
            val routeDns = if (tunneled) Dns { hostname ->
                if (hostname == canonicalHost) InetAddress.getAllByName(routeHost).toList()
                else Dns.SYSTEM.lookup(hostname)
            } else null
            val defaultPort = if (service.webDavHttps) 443 else 80
            val originalAuthority = HostAddress.authority(
                canonicalHost,
                service.remotePort,
                defaultPort,
            )
            WebDavClient(
                baseUrl = base,
                user = service.user,
                password = service.password,
                dns = routeDns,
                hostHeader = originalAuthority,
            )
        }
    }
}
