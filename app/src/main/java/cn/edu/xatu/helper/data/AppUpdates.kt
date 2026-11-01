package cn.edu.xatu.helper.data

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

const val UPDATE_INTERVAL = 7L * 24 * 60 * 60 * 1000
const val RELEASES_URL = "https://github.com/Kelejun/XATUHelper-Android/releases"
private const val RELEASE_API = "https://api.github.com/repos/Kelejun/XATUHelper-Android/releases/latest"

data class AppRelease(val version: String, val notes: String, val url: String)
data class UpdateCache(
    val lastSuccess: Long = 0, val nextAttempt: Long = 0, val failures: Int = 0,
    val etag: String? = null, val release: AppRelease? = null
) {
    fun due(now: Long): Boolean = (lastSuccess == 0L || now - lastSuccess >= UPDATE_INTERVAL) && now >= nextAttempt
}

interface UpdateStorage {
    suspend fun read(): UpdateCache
    suspend fun write(cache: UpdateCache)
}

/** App-level preferences survive school-account logout. No credentials or school cookies are used. */
class UpdatePreferences(context: Context) : UpdateStorage {
    private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val gson = Gson()
    override suspend fun read(): UpdateCache = withContext(Dispatchers.IO) {
        runCatching { gson.fromJson(preferences.getString("cache", null), UpdateCache::class.java) }.getOrNull() ?: UpdateCache()
    }
    // Persist before returning success, on the IO dispatcher, so restarts retain the schedule.
    @SuppressLint("ApplySharedPref")
    override suspend fun write(cache: UpdateCache): Unit = withContext(Dispatchers.IO) {
        if (!preferences.edit().putString("cache", gson.toJson(cache)).commit()) throw IOException("无法保存更新检查结果")
    }
}

/** Stable semantic versions only; never compare version strings lexicographically. */
fun releaseVersion(value: String): List<Long>? = Regex("^[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\+[0-9A-Za-z.-]+)?$")
    .matchEntire(value)?.groupValues?.drop(1)?.map { it.toLongOrNull() ?: return null }

fun isNewerRelease(candidate: String, current: String): Boolean {
    val a = releaseVersion(candidate) ?: return false
    val b = releaseVersion(current) ?: return false
    return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
}

data class UpdateResult(val cache: UpdateCache, val checked: Boolean)
class UpdateCheckException(val retryAt: Long) : IOException("无法连接 GitHub，请检查网络后重试")

class AppUpdateRepository(
    private val storage: UpdateStorage,
    // OkHttp's default ProxySelector follows the system proxy; deliberately no app proxy setting.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS).retryOnConnectionFailure(false).build(),
    private val endpoint: String = RELEASE_API,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    suspend fun cached(): UpdateCache = storage.read()
    suspend fun check(manual: Boolean = false): UpdateResult = mutex.withLock {
        val cache = storage.read()
        val now = clock()
        if (!manual && !cache.due(now)) return@withLock UpdateResult(cache, false)
        // Manual checks bypass the seven-day interval, but respect failure/rate-limit backoff.
        if (manual && now < cache.nextAttempt) throw UpdateCheckException(cache.nextAttempt)
        var retryAt = 0L
        try {
            val fresh = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(endpoint)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "XATUHelper-Android")
                if (cache.release != null) cache.etag?.let { request.header("If-None-Match", it) }
                client.newCall(request.build()).execute().use { response ->
                    if (!response.isSuccessful && response.code != 304) {
                        val seconds = response.header("Retry-After")?.toLongOrNull()?.coerceIn(0, 604800)
                        val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()
                        retryAt = maxOf(seconds?.let { now + it * 1000 } ?: 0,
                            if (response.code == 403 || response.code == 429) reset?.let { it.coerceIn(0, now / 1000 + 604800) * 1000 } ?: 0 else 0)
                        throw IOException("GitHub HTTP ${response.code}")
                    }
                    val release = if (response.code == 304) cache.release ?: throw IOException("缺少版本缓存") else {
                        val json = JsonParser.parseString(response.body?.string() ?: throw IOException("空响应")).asJsonObject
                        val version = json.get("tag_name")?.asString ?: throw IOException("缺少版本号")
                        if (json.get("draft")?.asBoolean != false || json.get("prerelease")?.asBoolean != false || releaseVersion(version) == null)
                            throw IOException("无效的正式版本")
                        // Build the URL locally to keep confirmation links on the official repository.
                        AppRelease(version, json.get("body")?.takeUnless { it.isJsonNull }?.asString.orEmpty().take(4000),
                            "$RELEASES_URL/tag/${version}")
                    }
                    UpdateCache(clock(), etag = response.header("ETag") ?: if (response.code == 304) cache.etag else null, release = release)
                }
            }
            storage.write(fresh)
            UpdateResult(fresh, true)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            val failures = (cache.failures.coerceIn(0, 7) + 1)
            val delay = (15L * 60 * 1000 * (1L shl (failures - 1))).coerceAtMost(24L * 60 * 60 * 1000)
            val failed = cache.copy(failures = failures, nextAttempt = maxOf(clock() + delay, retryAt))
            storage.write(failed)
            throw UpdateCheckException(failed.nextAttempt)
        }
    }
}
