package com.xatune.app.ui

import android.app.Application
import androidx.lifecycle.*
import com.google.gson.JsonParser
import com.xatune.app.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

data class Announcement(val id: String, val title: String, val body: String, val expiresAt: Long?)

class AnnouncementViewModel(application: Application) : AndroidViewModel(application), DefaultLifecycleObserver {
    val pending = MutableStateFlow<List<Announcement>>(emptyList())
    private val preferences = application.getSharedPreferences("announcements", 0)
    private val base = BuildConfig.ANNOUNCEMENTS_BASE_URL.trim().trimEnd('/')
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private var job: Job? = null
    private var expiry: Job? = null
    override fun onStart(owner: LifecycleOwner) { refresh() }
    private fun refresh() {
        val url = base.toHttpUrlOrNull() ?: return
        if ((!url.isHttps && !BuildConfig.DEBUG) || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null || job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                val (items, serverTime) = withContext(Dispatchers.IO) {
                    val endpoint = url.newBuilder().addPathSegments("v1/announcements").addQueryParameter("platform", "android").build()
                    client.newCall(Request.Builder().url(endpoint).header("Accept", "application/json").build()).execute().use { response ->
                        require(response.code == 200)
                        val source = response.body!!.source()
                        source.request(4L * 1024 * 1024 + 1)
                        require(source.buffer.size <= 4L * 1024 * 1024)
                        val bytes = source.buffer.readByteArray()
                        require(bytes.size <= 4 * 1024 * 1024)
                        val json = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                        val now = json["server_time"].asLong
                        val rows = json["announcements"].asJsonArray
                        require(now > 0 && rows.size() <= 100)
                        val list = rows.map { element ->
                            val row = element.asJsonObject
                            val id = row["id"].asString
                            java.util.UUID.fromString(id)
                            val title = row["title"].asString
                            val body = row["body"].asString
                            val expires = row["expires_at"].takeUnless { it.isJsonNull }?.asLong
                            require(title.isNotBlank() && title.codePointCount(0, title.length) <= 120 && body.isNotBlank() && body.codePointCount(0, body.length) <= 10000)
                            require(row["platforms"].asJsonArray.any { it.asString == "android" } && (expires == null || expires > now))
                            Announcement(id, title, body, expires)
                        }
                        require(list.map { it.id }.distinct().size == list.size)
                        list to now
                    }
                }
                val seen = preferences.getStringSet(base, emptySet()).orEmpty().intersect(items.map { it.id }.toSet())
                preferences.edit().putStringSet(base, seen).apply()
                pending.value = items.filter { it.id !in seen }
                expiry?.cancel()
                val received = android.os.SystemClock.elapsedRealtime()
                expiry = viewModelScope.launch {
                    while (isActive) {
                        val next = pending.value.mapNotNull { it.expiresAt }.minOrNull() ?: break
                        val now = serverTime + android.os.SystemClock.elapsedRealtime() - received
                        delay((next - now).coerceIn(1, 86400000))
                        val current = serverTime + android.os.SystemClock.elapsedRealtime() - received
                        pending.value = pending.value.filter { (it.expiresAt ?: Long.MAX_VALUE) > current }
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { pending.value = emptyList(); expiry?.cancel() }
        }
    }
    fun dismiss(id: String) {
        val seen = preferences.getStringSet(base, emptySet()).orEmpty() + id
        preferences.edit().putStringSet(base, seen).apply()
        pending.value = pending.value.filter { it.id != id }
    }
}
