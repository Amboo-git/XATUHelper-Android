package com.xatune.app

import com.xatune.app.data.*
import okhttp3.CookieJar
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NetworkTest {
    @Test fun directLoginSubmitsEncryptedPasswordAndHiddenFields() {
        MockWebServer().use { server ->
            server.start()
            val base = server.url("/eams/").toString(); val login = server.url("/login").toString()
            server.enqueue(MockResponse().setBody("""<form><input id='pwdEncryptSalt' value='0123456789abcdef'><input id='execution' value='e1s1'><input type='hidden' name='lt' value='ticket-token'></form>"""))
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", base + "homeExt.action"))
            server.enqueue(MockResponse().setBody("<html>教务首页</html>"))
            SchoolNetwork(CookieJar.NO_COOKIES, base, login).login("demo", "secret")
            assertEquals("GET", server.takeRequest().method)
            val request = server.takeRequest(); val body = request.body.readUtf8()
            assertTrue(body.contains("execution=e1s1")); assertTrue(body.contains("lt=ticket-token"))
            assertTrue(body.contains("username=demo")); assertFalse(body.contains("secret"))
        }
    }
    @Test fun passwordLoginUsesItsOwnFormWhenIdsAreDuplicated() {
        MockWebServer().use { server ->
            server.start()
            val base = server.url("/eams/").toString(); val login = server.url("/login").toString()
            server.enqueue(MockResponse().setBody("""<form id='qr'><input id='execution' value='qr-token'></form><form id='sms'><input id='pwdEncryptSalt' value='wrong-salt'><input id='execution' value='sms-token'></form><form id='pwdFromId'><input name='password'><input id='pwdEncryptSalt' value='0123456789abcdef'><input id='execution' value='password-token'></form>"""))
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", base + "homeExt.action"))
            server.enqueue(MockResponse().setBody("教务首页"))
            SchoolNetwork(CookieJar.NO_COOKIES, base, login).login("demo", "secret")
            server.takeRequest()
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("execution=password-token")); assertFalse(body.contains("sms-token")); assertFalse(body.contains("qr-token"))
        }
    }
    @Test fun captchaOpensBrowserInsteadOfSubmittingPassword() {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody("<input id='pwdEncryptSalt'><script>captchaSwitch=1;</script>"))
            val network = SchoolNetwork(CookieJar.NO_COOKIES, server.url("/eams/").toString(), server.url("/login").toString())
            assertThrows(WebLoginRequired::class.java) { network.login("demo", "secret") }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun expiredEamsResponseIsRecognized() {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody("<input name='password'>"))
            val network = SchoolNetwork(CookieJar.NO_COOKIES, server.url("/eams/").toString(), server.url("/login").toString())
            assertThrows(SessionExpired::class.java) { network.shell() }
        }
    }
    @Test fun httpErrorsDoNotBecomeEmptyTimetables() {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setResponseCode(503))
            val network = SchoolNetwork(CookieJar.NO_COOKIES, server.url("/eams/").toString(), server.url("/login").toString())
            assertThrows(java.io.IOException::class.java) { network.shell() }
        }
    }
}
