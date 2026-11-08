package cn.edu.xatu.helper.platform

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Custom coordinates stay inside the check-in WebView; never changes Android's location provider. */
internal class CheckinLocationBridge(private val web: WebView, private val topUrl: () -> String = { web.url.orEmpty() }) {
    private val preferences = CheckinLocationPreferences(web.context)
    private var handler: ScriptHandler? = null
    private var listening = false
    val supportsDocumentStart = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    val source: String by lazy { web.context.assets.open("checkin-location.js").bufferedReader().use { it.readText() } }

    fun attach(injectAtDocumentStart: Boolean = true) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        // Map SDKs use cross-origin iframes. ReplyProxy targets the requesting frame rather
        // than evaluating a callback in the main document. Never replies with actual GPS data.
        WebViewCompat.addWebMessageListener(web, "XatuCheckinLocation", setOf("*")) { _, message, _, _, reply ->
            val request = runCatching { JSONObject(message.data.orEmpty()) }.getOrNull() ?: return@addWebMessageListener
            val id = request.optLong("id", -1)
            if (id < 0) return@addWebMessageListener
            val result = JSONObject().put("id", id)
            val settings = preferences.read()
            when {
                !mayOverride(topUrl()) || !settings.enabled -> result.put("mode", "native")
                settings.coordinate == null -> result.put("mode", "error").put("message", "请先在设置中选择有效的签到位置")
                else -> {
                    val point = settings.coordinate.gps()
                    result.put("mode", "custom").put("latitude", point.latitude).put("longitude", point.longitude)
                }
            }
            WebDiagnosticLog.record(web.context, "checkin-bridge", "reply", "mode=${result.optString("mode")} id=$id")
            runCatching { reply.postMessage(result.toString()) }
        }
        listening = true
        if (injectAtDocumentStart && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) handler = WebViewCompat.addDocumentStartJavaScript(web, source, setOf("*"))
    }
    fun close() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) handler?.remove()
        handler = null
        if (listening && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(web, "XatuCheckinLocation")
        listening = false
    }
    companion object {
        fun mayOverride(topUrl: String): Boolean {
            val url = topUrl.toHttpUrlOrNull() ?: return false
            return url.scheme == "https" && url.host == "ehall.xatu.edu.cn" && url.port == 443 && url.encodedPath.startsWith("/xsfw/sys/swmqdzsapp/")
        }
    }
}
