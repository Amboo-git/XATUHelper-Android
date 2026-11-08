package cn.edu.xatu.helper.platform

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import cn.edu.xatu.helper.HelperApplication
import cn.edu.xatu.helper.data.EamsDocuments
import java.io.Closeable

internal fun configureCampusWebView(web: WebView) {
    web.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    web.settings.useWideViewPort = true
    web.settings.loadWithOverviewMode = true
    web.settings.builtInZoomControls = true
    web.settings.displayZoomControls = false
}

internal class CampusWebDocuments(context: Context) : Closeable {
    private val documents = EamsDocuments((context.applicationContext as HelperApplication).cookies)
    fun redirect(web: WebView, request: WebResourceRequest): Boolean {
        if (!request.isRedirect || !EamsDocuments.handles(request.url.toString(), request.method, request.isForMainFrame)) return false
        // WebView only intercepts the initial URL of an HTTP redirect chain. Restart an
        // EAMS destination as its own navigation so a CAS redirect cannot bypass stripping.
        web.loadUrl(request.url.toString())
        return true
    }
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (!EamsDocuments.handles(url, request.method, request.isForMainFrame, request.requestHeaders)) return null
        return try {
            val doc = documents.load(url, request.requestHeaders)
            WebResourceResponse(doc.mime, doc.charset, doc.status, doc.reason, doc.headers, doc.body)
        } catch (_: Exception) {
            // Do not retry through the legacy WebView path: that would restore the bad header.
            WebResourceResponse("text/plain", "UTF-8", 502, "Bad Gateway", mapOf("Cache-Control" to "no-store"),
                "教务网页加载失败，请检查网络后重试。".byteInputStream())
        }
    }
    override fun close() = documents.close()
}
