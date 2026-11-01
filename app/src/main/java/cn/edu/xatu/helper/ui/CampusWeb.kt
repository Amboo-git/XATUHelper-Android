package cn.edu.xatu.helper.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cn.edu.xatu.helper.data.SchoolUrls
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CampusLinks {
    fun mayLocate(page: String, origin: String, topUrl: String): Boolean {
        val source = origin.toHttpUrlOrNull() ?: return false
        val top = topUrl.toHttpUrlOrNull() ?: return false
        return page == "checkin" && source.scheme == "https" && source.host == "ehall.xatu.edu.cn" && source.port == 443 && top.scheme == "https" && top.host == source.host && top.port == 443
    }
    val pages = linkedMapOf(
        "planWeb" to ("学业完成情况" to "http://jwgl2018.xatu.edu.cn/eams/myPlanCompl.action"),
        "charge" to ("电费充值" to "https://jfpay.xatu.edu.cn/casLogin/"),
        "netfee" to ("校园网充值" to "https://self.xatu.edu.cn/Self/sso/packageChange"),
        "checkin" to ("签到" to "https://ehall.xatu.edu.cn/xsfw/sys/swmqdzsapp/*default/index.do#/jrqd"),
        "leave" to ("请销假" to "https://ehall.xatu.edu.cn/xsfw/sys/swmxsqjapp/*default/index.do?wxType=1"),
        "eams" to ("教务系统" to "http://jwgl2018.xatu.edu.cn/"),
        "ehall" to ("一网通办" to "https://ywtb.xatu.edu.cn/")
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable fun CampusWeb(page: String, onClose: () -> Unit) {
    val (title, url) = CampusLinks.pages.getValue(page)
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingLocation by remember { mutableStateOf<Pair<String, GeolocationPermissions.Callback>?>(null) }
    var files by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> files?.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray()); files = null }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        pendingLocation?.let { (origin, callback) -> callback.invoke(origin, granted.values.any { it }, false) }; pendingLocation = null
    }
    BackHandler { if (web?.canGoBack() == true) web?.goBack() else onClose() }
    DisposableEffect(Unit) { onDispose {
        pendingLocation?.let { it.second.invoke(it.first, false, false) }; files?.onReceiveValue(null)
        web?.apply { stopLoading(); webChromeClient = null; webViewClient = WebViewClient(); removeAllViews(); destroy() }; web = null
    } }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭网页") } }, actions = { IconButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web?.url ?: url))) }.onFailure { error = "没有可打开网页的浏览器" } }) { Icon(Icons.Default.OpenInNew, "在浏览器中打开") }; IconButton(onClick = { error = null; web?.reload() }) { Icon(Icons.Default.Refresh, "重新加载网页") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (page == "checkin") Text("定位仅用于当前学校签到网页，需你授权后提供。", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx -> WebView(ctx).apply {
                web = this
                settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                settings.allowFileAccess = false; settings.allowContentAccess = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setGeolocationEnabled(page == "checkin")
                webChromeClient = object : WebChromeClient() {
                    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                        val source = Uri.parse(origin)
                        val top = Uri.parse(web?.url.orEmpty())
                        if (!CampusLinks.mayLocate(page, origin, web?.url.orEmpty())) { callback.invoke(origin, false, false); return }
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) callback.invoke(origin, true, false)
                        else { pendingLocation?.let { it.second.invoke(it.first, false, false) }; pendingLocation = origin to callback; permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
                    }
                    override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                        files?.onReceiveValue(null); files = callback
                        filePicker.launch(params.acceptTypes.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotBlank() }.toTypedArray().ifEmpty { arrayOf("*/*") }); return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url.toString()
                        if (SchoolUrls.trusted(target)) return false
                        if (request.hasGesture() && request.url.scheme in listOf("https", "http", "tel", "mailto", "alipays", "weixin")) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }.onFailure { error = "没有可打开此链接的应用" }
                        } else error = "已停止打开不受支持的跳转地址"
                        return true
                    }
                    override fun onPageStarted(view: WebView, url: String, icon: android.graphics.Bitmap?) { loading = true; error = null }
                    override fun onPageFinished(view: WebView, url: String) { loading = false; CookieManager.getInstance().flush() }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, e: WebResourceError) { if (request.isForMainFrame) { loading = false; error = "网页加载失败，请检查网络后重试" } }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) { if (request.isForMainFrame) { loading = false; error = "学校服务返回 HTTP ${response.statusCode}" } }
                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, sslError: android.net.http.SslError) { handler.cancel(); loading = false; error = "学校网页的安全连接校验失败" }
                }
                loadUrl(url)
            } })
        }
    }
}
