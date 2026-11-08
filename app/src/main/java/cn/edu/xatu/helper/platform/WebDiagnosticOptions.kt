package cn.edu.xatu.helper.platform

import android.content.Context
import androidx.core.content.edit
import cn.edu.xatu.helper.BuildConfig

internal object WebDiagnosticOptions {
    fun matchParent(context: Context): Boolean = BuildConfig.WEB_DIAGNOSTICS &&
        context.getSharedPreferences("web-diagnostic-options", Context.MODE_PRIVATE).getBoolean("match-parent", false)
    fun setMatchParent(context: Context, enabled: Boolean) {
        context.getSharedPreferences("web-diagnostic-options", Context.MODE_PRIVATE).edit { putBoolean("match-parent", enabled) }
        WebDiagnosticLog.record(context, "options", "layout-mode", if (enabled) "match-parent" else "baseline")
    }
}
