package com.xatune.app

import com.xatune.app.data.EamsDocuments
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class EamsDocumentsTest {
    @Test fun onlySchoolDocumentsAndFramesGetReplayed() {
        val url = "http://jwgl2018.xatu.edu.cn/eams/homeExt.action"
        assertTrue(EamsDocuments.handles(url, "GET", true))
        assertFalse(EamsDocuments.handles(url, "POST", true))
        assertFalse(EamsDocuments.handles(url, "GET", false))
        assertTrue(EamsDocuments.handles(url, "GET", false, mapOf("Upgrade-Insecure-Requests" to "1")))
        assertTrue(EamsDocuments.handles(url, "GET", false, mapOf("sec-fetch-dest" to "iframe")))
        assertFalse(EamsDocuments.handles(url, "GET", false, mapOf("Sec-Fetch-Dest" to "script")))
        assertFalse(EamsDocuments.handles(url, "GET", false, mapOf("Upgrade-Insecure-Requests" to "1", "X-Requested-With" to "XMLHttpRequest")))
        assertFalse(EamsDocuments.handles("https://authserver.xatu.edu.cn/login", "GET", true))
        assertFalse(EamsDocuments.handles("http://jwgl2018.xatu.edu.cn:8080/", "GET", true))
        assertFalse(EamsDocuments.handles("http://jwgl2018.xatu.edu.cn.evil.example/", "GET", true))
    }

    @Test fun stripsHeaderAndUsesCurrentCookiesWhileKeepingUserAgentAndReferer() {
        MockWebServer().use { server ->
            server.start()
            var stored = listOf(Cookie.Builder().name("session").value("fresh").hostOnlyDomain(server.hostName).build())
            val jar = object : CookieJar {
                override fun loadForRequest(url: HttpUrl) = stored
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { if (cookies.isNotEmpty()) stored = cookies }
            }
            server.enqueue(MockResponse().addHeader("Set-Cookie", "session=updated; Path=/; HttpOnly")
                .addHeader("Content-Type", "text/html; charset=GB18030").setBody("<link rel='stylesheet' href='main.css'>"))
            EamsDocuments(jar).use { loader ->
                val doc = loader.load(server.url("/eams/homeExt.action").toString(), mapOf(
                    "x-ReQuEsTeD-wItH" to "com.xatune.app", "Cookie" to "session=stale",
                    "User-Agent" to "fixture-agent", "Referer" to "https://authserver.xatu.edu.cn/",
                    "If-None-Match" to "old-etag"))
                assertTrue(doc.body.bufferedReader().use { it.readText() }.contains("stylesheet"))
                assertEquals("GB18030", doc.charset)
                assertEquals("no-store", doc.headers["Cache-Control"])
                val request = server.takeRequest()
                assertNull(request.getHeader("X-Requested-With"))
                assertNull(request.getHeader("If-None-Match"))
                assertEquals("session=fresh", request.getHeader("Cookie"))
                assertEquals("fixture-agent", request.getHeader("User-Agent"))
                assertEquals("https://authserver.xatu.edu.cn/", request.getHeader("Referer"))
                assertEquals("updated", stored.single().value)
                assertFalse(doc.headers.keys.any { it.equals("Set-Cookie", true) })
            }
        }
    }

    @Test fun decodesGzipAndPreservesHttpFailureStatus() {
        MockWebServer().use { server ->
            server.start()
            val buffer = Buffer()
            GzipSink(buffer).buffer().use { it.writeUtf8("unavailable") }
            server.enqueue(MockResponse().setResponseCode(503).addHeader("Content-Encoding", "gzip")
                .addHeader("Content-Type", "text/plain; charset=UTF-8").setBody(buffer))
            EamsDocuments(CookieJar.NO_COOKIES).use { loader ->
                val doc = loader.load(server.url("/").toString(), emptyMap())
                assertEquals(503, doc.status)
                assertEquals("unavailable", doc.body.bufferedReader().use { it.readText() })
                assertFalse(doc.headers.keys.any { it.equals("Content-Encoding", true) || it.equals("Content-Length", true) })
            }
        }
    }

    @Test fun redirectsNavigateToRealTrustedUrlAndNeverFollowUntrustedHosts() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "http://authserver.xatu.edu.cn/authserver/login?service=a&ticket=b"))
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://evil.example/"))
            EamsDocuments(CookieJar.NO_COOKIES).use { loader ->
                val doc = loader.load(server.url("/").toString(), emptyMap())
                val html = doc.body.bufferedReader().use { it.readText() }
                assertTrue(html.contains("http-equiv=\"refresh\""))
                assertTrue(html.contains("https://authserver.xatu.edu.cn/authserver/login?service=a&amp;ticket=b"))
                assertEquals(1, server.requestCount)
                assertThrows(IllegalStateException::class.java) { loader.load(server.url("/").toString(), emptyMap()) }
                assertEquals(2, server.requestCount)
            }
        }
    }
}
