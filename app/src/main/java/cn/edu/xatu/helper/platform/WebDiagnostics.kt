package cn.edu.xatu.helper.platform

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import cn.edu.xatu.helper.BuildConfig
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal object WebDiagnosticLog {
    private val worker = Executors.newSingleThreadExecutor()
    private var initialized = false
    private fun directory(context: Context) = File(context.filesDir, "web-diagnostics").apply { mkdirs() }
    fun record(context: Context, session: String, event: String, detail: String = "") {
        if (!BuildConfig.WEB_DIAGNOSTICS) return
        val app = context.applicationContext
        val line = "${Instant.now()} [$session] $event ${WebDiagnosticText.clean(detail)}\n"
        worker.execute { runCatching {
            val dir = directory(app)
            val file = File(dir, "current.txt")
            if (!initialized) {
                initialized = true
                file.appendText("\nXATune ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT}) code=${BuildConfig.VERSION_CODE}\n" +
                    "device=${Build.MANUFACTURER}/${Build.MODEL} Android=${Build.VERSION.RELEASE} SDK=${Build.VERSION.SDK_INT}\n")
            }
            if (file.length() > 2 * 1024 * 1024) {
                File(dir, "previous-2.txt").delete()
                File(dir, "previous-1.txt").renameTo(File(dir, "previous-2.txt"))
                file.renameTo(File(dir, "previous-1.txt"))
            }
            file.appendText(line)
        } }
    }
    // Runs after all already queued writes; the exported file is a consistent snapshot.
    fun export(context: Context, output: OutputStream) {
        worker.submit<Unit> {
            output.bufferedWriter().use { writer ->
                writer.write("XATune WebView diagnostics\nVersion ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT})\nExport ${Instant.now()}\n")
                val dir = directory(context.applicationContext)
                for (name in listOf("previous-2.txt", "previous-1.txt", "current.txt")) {
                    val file = File(dir, name)
                    if (file.exists()) { writer.write("\n--- $name ---\n"); file.bufferedReader().use { it.copyTo(writer) } }
                }
            }
        }.get()
    }
}

internal class WebDiagnostics(private val web: WebView, page: String) {
    private val session = "$page-${next.incrementAndGet()}"
    private val active get() = BuildConfig.WEB_DIAGNOSTICS && !closed
    @Volatile private var closed = false
    private var requests = AtomicInteger()
    private var consoleCount = 0
    private var generation = 0
    private var scheduledGeneration = -1
    private val matchParent = WebDiagnosticOptions.matchParent(web.context)
    private var handler: androidx.webkit.ScriptHandler? = null
    private val script by lazy { web.context.assets.open("web-diagnostics.js").bufferedReader().use { it.readText() } }
    fun event(name: String, detail: String = "") { if (active) WebDiagnosticLog.record(web.context, session, name, detail) }
    fun attach() {
        if (!active) return
        val pkg = WebViewCompat.getCurrentWebViewPackage(web.context)
        event("open", "engine=${pkg?.packageName}/${pkg?.versionName} viewport=${web.resources.displayMetrics} fontScale=${web.resources.configuration.fontScale} layoutMode=${if (matchParent) "match-parent" else "baseline"} UA=${web.settings.userAgentString} js=${web.settings.javaScriptEnabled} storage=${web.settings.domStorageEnabled} mixed=${web.settings.mixedContentMode} thirdPartyCookies=${CookieManager.getInstance().acceptThirdPartyCookies(web)}")
        if (matchParent) web.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        web.addOnAttachStateChangeListener(attachListener)
        val documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        event("instrumentation", "documentStart=$documentStart")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) runCatching { handler = WebViewCompat.addDocumentStartJavaScript(web, script, setOf("*")) }
            .onFailure { event("instrumentation-failed", it.javaClass.simpleName) }
        web.addOnLayoutChangeListener(layoutListener)
    }
    private fun nativeState(reason: String) {
        val parents = generateSequence(web.parent) { it.parent }.filterIsInstance<View>().take(5).joinToString(";") {
            "${it.javaClass.simpleName}:size=${it.width}x${it.height},lp=${it.layoutParams?.width}x${it.layoutParams?.height},layer=${it.layerType},hardware=${it.isHardwareAccelerated}"
        }
        event("native-state", "$reason attached=${web.isAttachedToWindow} hardware=${web.isHardwareAccelerated} layer=${web.layerType} measured=${web.measuredWidth}x${web.measuredHeight} size=${web.width}x${web.height} lp=${web.layoutParams?.width}x${web.layoutParams?.height} layoutMode=${if (matchParent) "match-parent" else "baseline"} parents=$parents")
    }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) { web.post { if (active) nativeState("attached") } }
        override fun onViewDetachedFromWindow(view: View) { event("detached") }
    }
    private val layoutListener = android.view.View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
        if (r-l != or-ol || b-t != ob-ot) nativeState("layout-${r-l}x${b-t}")
    }
    fun request(request: WebResourceRequest) {
        if (active && requests.incrementAndGet() <= 1500) event("resource-request", "${request.method} main=${request.isForMainFrame} ${WebDiagnosticText.url(request.url.toString())}")
    }
    fun navigation(request: WebResourceRequest, allowed: Boolean) = event("navigation", "allowed=$allowed main=${request.isForMainFrame} gesture=${request.hasGesture()} ${WebDiagnosticText.url(request.url.toString())}")
    fun started(url: String) {
        generation++; requests.set(0); consoleCount = 0
        event("page-start", WebDiagnosticText.url(url))
    }
    fun finished(url: String) {
        event("page-finish", WebDiagnosticText.url(url))
        if (!active) return
        web.evaluateJavascript(script, null)
        if (scheduledGeneration == generation) return
        scheduledGeneration = generation
        val expected = generation
        for (delay in listOf(1000L, 5000L, 15000L)) web.postDelayed({
            if (active && generation == expected) capture("after-${delay}ms")
        }, delay)
    }
    fun capture(reason: String = "manual") {
        if (!active) return
        nativeState(reason)
        event("snapshot", "$reason width=${web.width} height=${web.height} contentHeight=${web.contentHeight} scroll=${web.scrollX},${web.scrollY} progress=${web.progress} url=${WebDiagnosticText.url(web.url.orEmpty())}")
        web.evaluateJavascript("window.__xatuDiagnosticSnapshot && window.__xatuDiagnosticSnapshot()", null)
    }
    fun console(message: ConsoleMessage): Boolean {
        if (!active || consoleCount++ >= 2000) return false
        if (message.message().startsWith("XATU_DIAG:")) event("web-event", message.message().removePrefix("XATU_DIAG:"))
        else if (message.messageLevel() in listOf(ConsoleMessage.MessageLevel.ERROR, ConsoleMessage.MessageLevel.WARNING)) {
            // Page console messages may contain account records or response bodies. Keep only location and error class.
            val category = Regex("(TypeError|ReferenceError|SyntaxError|RangeError|SecurityError|NetworkError|Error)").find(message.message())?.value ?: "console"
            event("console", "${message.messageLevel()} $category ${WebDiagnosticText.url(message.sourceId())}:${message.lineNumber()}")
        }
        return false
    }
    fun http(request: WebResourceRequest, response: WebResourceResponse) = event("http-error", "status=${response.statusCode} main=${request.isForMainFrame} mime=${response.mimeType} ${WebDiagnosticText.url(request.url.toString())}")
    fun error(request: WebResourceRequest, error: WebResourceError) = event("network-error", "code=${error.errorCode} main=${request.isForMainFrame} ${WebDiagnosticText.url(request.url.toString())}")
    fun ssl(error: android.net.http.SslError) = event("ssl-error", "code=${error.primaryError} ${WebDiagnosticText.url(error.url.orEmpty())}")
    fun close() {
        if (!active) return
        event("close"); closed = true; generation++
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) handler?.remove()
        handler = null
        web.removeOnLayoutChangeListener(layoutListener)
        web.removeOnAttachStateChangeListener(attachListener)
    }
    companion object { private val next = AtomicInteger() }
}
