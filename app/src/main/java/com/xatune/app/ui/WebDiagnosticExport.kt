package com.xatune.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.xatune.app.BuildConfig
import com.xatune.app.platform.WebDiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WebDiagnosticExport(beforeExport: () -> Unit = {}) {
    if (!BuildConfig.WEB_DIAGNOSTICS) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val success = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { WebDiagnosticLog.export(context, it) }
                    ?: error("Cannot open export destination")
            }.isSuccess }
            busy = false
            Toast.makeText(context, if (success) "诊断文本已导出" else "导出失败，请重新选择保存位置", Toast.LENGTH_LONG).show()
        }
    }
    TextButton(enabled = !busy, onClick = {
        beforeExport()
        WebDiagnosticLog.record(context, "export", "requested")
        picker.launch("XATune-WebView-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.txt")
    }) { Text(if (busy) "导出中…" else "导出诊断") }
}
