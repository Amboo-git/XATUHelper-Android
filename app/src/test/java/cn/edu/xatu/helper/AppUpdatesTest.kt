package cn.edu.xatu.helper

import cn.edu.xatu.helper.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    private class MemoryStorage(var cache: UpdateCache = UpdateCache()) : UpdateStorage {
        override suspend fun read() = cache
        override suspend fun write(cache: UpdateCache) { this.cache = cache }
    }
    private fun release(tag: String = "v1.2.0", prerelease: Boolean = false) = MockResponse()
        .addHeader("ETag", "\"release-1\"")
        .setBody("""{"tag_name":"$tag","draft":false,"prerelease":$prerelease,"body":"更新说明","html_url":"https://untrusted.example/download"}""")

    @Test fun stableVersionsCompareNumerically() {
        assertTrue(isNewerRelease("v1.10.0", "1.9.9"))
        assertTrue(isNewerRelease("2.0.0", "1.99.99"))
        assertFalse(isNewerRelease("v1.1.0", "1.1.0"))
        assertFalse(isNewerRelease("1.0.9", "1.1.0"))
        assertFalse(isNewerRelease("1.2.0-beta.1", "1.1.0"))
        assertFalse(isNewerRelease("1.2.0/../../evil", "1.1.0"))
        assertFalse(isNewerRelease("99999999999999999999.0.0", "1.1.0"))
    }
    @Test fun firstLaunchThenSevenDaysAndConditionalCache() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val store = MemoryStorage()
            var now = 1_000_000L
            val repo = AppUpdateRepository(store, endpoint = server.url("/").toString(), clock = { now })
            server.enqueue(release())
            assertTrue(repo.check().checked)
            assertEquals("https://github.com/Kelejun/XATUHelper-Android/releases/tag/v1.2.0", store.cache.release?.url)
            val request = server.takeRequest()
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
            now += UPDATE_INTERVAL - 1
            assertFalse(repo.check().checked)
            assertEquals(1, server.requestCount)
            now++
            server.enqueue(MockResponse().setResponseCode(304))
            assertTrue(repo.check().checked)
            assertEquals("\"release-1\"", server.takeRequest().getHeader("If-None-Match"))
            assertEquals(now, store.cache.lastSuccess)
            assertEquals("v1.2.0", store.cache.release?.version)
        }
    }
    @Test fun failureKeepsSuccessAndReleaseAndBacksOffAcrossRepositoryInstances() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            var now = UPDATE_INTERVAL + 100L
            val saved = AppRelease("1.2.0", "notes", RELEASES_URL)
            val store = MemoryStorage(UpdateCache(lastSuccess = 1, release = saved))
            val repo = AppUpdateRepository(store, endpoint = server.url("/").toString(), clock = { now })
            server.enqueue(MockResponse().setResponseCode(503))
            try { repo.check(); fail() } catch (_: UpdateCheckException) { }
            assertEquals(1L, store.cache.lastSuccess)
            assertEquals(saved, store.cache.release)
            assertEquals(now + 15 * 60_000, store.cache.nextAttempt)
            assertFalse(AppUpdateRepository(store, endpoint = server.url("/").toString(), clock = { now }).check().checked)
            try { repo.check(manual = true); fail() } catch (_: UpdateCheckException) { }
            assertEquals(1, server.requestCount)
            now = store.cache.nextAttempt
            server.enqueue(MockResponse().setResponseCode(503))
            try { repo.check(); fail() } catch (_: UpdateCheckException) { }
            assertEquals(now + 30 * 60_000, store.cache.nextAttempt)
            now = store.cache.nextAttempt
            server.enqueue(release())
            repo.check()
            assertEquals(0, store.cache.failures)
            assertEquals(0L, store.cache.nextAttempt)
        }
    }
    @Test fun manualCheckBypassesSevenDayInterval() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val store = MemoryStorage(UpdateCache(lastSuccess = 100))
            val repo = AppUpdateRepository(store, endpoint = server.url("/").toString(), clock = { 101 })
            server.enqueue(release("v1.1.0"))
            assertTrue(repo.check(manual = true).checked)
            assertEquals(101L, store.cache.lastSuccess)
        }
    }
    @Test fun malformedAndPrereleaseResponsesNeverBecomeSuccessfulChecks() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val store = MemoryStorage()
            val repo = AppUpdateRepository(store, endpoint = server.url("/").toString(), clock = { 100 })
            server.enqueue(release("v1.2.0", prerelease = true))
            try { repo.check(); fail() } catch (_: UpdateCheckException) { }
            assertEquals(0L, store.cache.lastSuccess)
            assertNull(store.cache.release)
            store.cache = UpdateCache()
            server.enqueue(MockResponse().setResponseCode(304))
            try { repo.check(); fail() } catch (_: UpdateCheckException) { }
            assertEquals(0L, store.cache.lastSuccess)
        }
    }
    @Test fun rateLimitResetIsRespected() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val store = MemoryStorage()
            val repo = AppUpdateRepository(store, endpoint = server.url("/").toString(), clock = { 1_000_000 })
            server.enqueue(MockResponse().setResponseCode(403).addHeader("X-RateLimit-Reset", "5000"))
            try { repo.check(); fail() } catch (_: UpdateCheckException) { }
            assertEquals(5_000_000L, store.cache.nextAttempt)
            assertFalse(repo.check().checked)
        }
    }
}
