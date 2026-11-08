package cn.edu.xatu.helper.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.edu.xatu.helper.data.SchoolUrls

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WebLogin(account: String, onClose: () -> Unit, onComplete: () -> Unit, onError: (String) -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var documents by remember { mutableStateOf<cn.edu.xatu.helper.platform.CampusWebDocuments?>(null) }
    var diagnostics by remember { mutableStateOf<cn.edu.xatu.helper.platform.WebDiagnostics?>(null) }
    var loading by remember { mutableStateOf(true) }
    var landed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val backModifier = Modifier.predictiveBack { if (web?.canGoBack() == true) web?.goBack() else onClose() }
    DisposableEffect(Unit) { onDispose {
        documents?.close(); documents = null
        diagnostics?.close(); diagnostics = null
        web?.apply { stopLoading(); webViewClient = WebViewClient(); destroy() }; web = null
    } }
    Scaffold(modifier = backModifier, topBar = {
        TopAppBar(title = { Text("统一身份认证") }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭网页登录") } },
            actions = { WebDiagnosticExport { diagnostics?.capture("export") }; TextButton(onClick = onComplete, enabled = landed && !loading) { Text("完成登录") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.padding(16.dp)) { InfoPanel("请使用学号 $account 登录；登录成功后点击右上角完成。") }
            LoadingProgress(loading)
            error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                TextButton(onClick = { error = null; web?.loadUrl(SchoolUrls.login) }) { Text("重新加载") }
            }
            AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                WebView(context).apply {
                    web = this
                    cn.edu.xatu.helper.platform.configureCampusWebView(this)
                    documents = cn.edu.xatu.helper.platform.CampusWebDocuments(this.context)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    diagnostics = cn.edu.xatu.helper.platform.WebDiagnostics(this, "login").also { it.attach() }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean = diagnostics?.console(message) ?: false
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? { diagnostics?.request(request); return documents?.intercept(request) }
                        override fun onPageCommitVisible(view: WebView, url: String) { diagnostics?.event("page-visible", cn.edu.xatu.helper.platform.WebDiagnosticText.url(url)) }
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean { diagnostics?.event("renderer-gone", "crashed=${detail.didCrash()}"); return false }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            diagnostics?.navigation(request, SchoolUrls.trusted(request.url.toString()))
                            if (!SchoolUrls.trusted(request.url.toString())) { error = "已停止打开学校域名以外的地址"; return true }
                            return documents?.redirect(view, request) ?: false
                        }
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { diagnostics?.started(url); loading = true; landed = false }
                        override fun onPageFinished(view: WebView, url: String) {
                            diagnostics?.finished(url)
                            loading = false
                            landed = android.net.Uri.parse(url).host == "jwgl2018.xatu.edu.cn" && !url.contains("login", ignoreCase = true)
                            CookieManager.getInstance().flush()
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, sslError: android.net.http.SslError) {
                            diagnostics?.ssl(sslError)
                            handler.cancel(); loading = false; error = "学校认证页面的安全连接校验失败，请稍后重试"
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            diagnostics?.http(request, response)
                            if (request.isForMainFrame) { loading = false; error = "学校认证服务返回 HTTP ${response.statusCode}，请稍后重试" }
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, e: WebResourceError) {
                            diagnostics?.error(request, e)
                            if (request.isForMainFrame) { loading = false; error = "认证网页加载失败，请检查网络后重试" }
                        }
                    }
                    loadUrl(SchoolUrls.login)
                }
            })
        }
    }
}
