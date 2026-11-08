package cn.edu.xatu.helper.data

import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

internal class EamsDocuments(cookies: CookieJar) : Closeable {
    private val client = OkHttpClient.Builder().cookieJar(cookies)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).build()

    data class Document(val status: Int, val reason: String, val mime: String, val charset: String?,
                        val headers: Map<String, String>, val body: InputStream)

    fun load(url: String, headers: Map<String, String>): Document {
        val request = Request.Builder().url(url).apply {
            headers.filterKeys { it.lowercase() !in excludedRequestHeaders }.forEach { (name, value) -> header(name, value) }
            header("Cache-Control", "no-cache")
        }.build()
        val response = client.newCall(request).execute()
        try {
            if (response.code in 300..399) {
                val target = response.header("Location")?.let { response.request.url.resolve(it) }
                // WebResourceResponse cannot represent redirects. Let the browser navigate to
                // the real URL, retaining its origin, history and relative resource resolution.
                check(target != null && SchoolUrls.trusted(target.toString())) { "Unsupported document redirect" }
                val escaped = target.toString().replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
                val html = "<!doctype html><meta charset=\"utf-8\"><meta http-equiv=\"refresh\" content=\"0;url=$escaped\">"
                response.close()
                return Document(200, "OK", "text/html", "UTF-8", mapOf("Cache-Control" to "no-store", "Referrer-Policy" to "no-referrer"), html.byteInputStream())
            }
            val type = response.body?.contentType()
            val body = response.body?.byteStream() ?: byteArrayOf().inputStream()
            val stream = object : FilterInputStream(body) {
                override fun close() { try { super.close() } finally { response.close() } }
            }
            val resultHeaders = response.headers.names().filter { it.lowercase() !in excludedResponseHeaders }
                .associateWith { response.headers.values(it).joinToString(", ") }.toMutableMap()
            resultHeaders["Cache-Control"] = "no-store"
            return Document(response.code, response.message.ifBlank { "HTTP ${response.code}" },
                type?.let { "${it.type}/${it.subtype}" } ?: "text/html", type?.charset()?.name(), resultHeaders, stream)
        } catch (error: Exception) { response.close(); throw error }
    }

    override fun close() { client.dispatcher.cancelAll(); client.connectionPool.evictAll() }

    companion object {
        fun handles(url: String, method: String, mainFrame: Boolean, headers: Map<String, String> = emptyMap()): Boolean {
            val target = url.toHttpUrlOrNull() ?: return false
            fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
            // Legacy Chromium omits Sec-Fetch-Dest, but marks frame navigations with
            // Upgrade-Insecure-Requests. AJAX and resource requests must keep their semantics.
            val frame = header("Sec-Fetch-Dest") in setOf("document", "iframe", "frame") || header("Upgrade-Insecure-Requests") == "1"
            val document = mainFrame || (frame && !header("X-Requested-With").equals("XMLHttpRequest", true))
            return document && method == "GET" && target.host == "jwgl2018.xatu.edu.cn" && SchoolUrls.trusted(url)
        }
        private val excludedRequestHeaders = setOf("x-requested-with", "cookie", "host", "connection", "accept-encoding", "content-length", "if-none-match", "if-modified-since")
        private val excludedResponseHeaders = setOf("set-cookie", "content-length", "content-encoding", "transfer-encoding", "connection", "cache-control")
    }
}
