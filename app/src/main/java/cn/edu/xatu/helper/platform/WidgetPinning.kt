package cn.edu.xatu.helper.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings

object WidgetPinning {
    const val PINNED = "cn.edu.xatu.helper.WIDGET_PINNED"
    const val CONFIRMED = "pin_confirmed"
    const val MANUAL_ADD = "请长按桌面，进入小组件列表添加「XATune」。"
    const val PERMISSION_HELP = "部分系统需要先允许「桌面快捷方式」或「添加到桌面」权限。请在应用权限或其他权限中开启，再返回申请添加。若没有此选项，可直接申请添加。"

    fun request(context: Context, provider: Class<out CampusWidgetProvider>): String {
        val manager = AppWidgetManager.getInstance(context)
        return try {
            if (!manager.isRequestPinAppWidgetSupported) return MANUAL_ADD
            val callback = PendingIntent.getBroadcast(context, provider.name.hashCode(),
                Intent(context, provider).setAction(PINNED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (manager.requestPinAppWidget(ComponentName(context, provider), null, callback))
                "已申请添加，请在桌面确认。若未弹出确认或未出现小组件，请先开启桌面添加权限后重试。$MANUAL_ADD"
            else "系统未接受添加申请，请检查桌面添加权限。$MANUAL_ADD"
        } catch (_: SecurityException) {
            "系统拦截了添加申请，请先开启桌面添加权限。$MANUAL_ADD"
        } catch (_: IllegalStateException) {
            "暂时无法申请添加，请解锁设备并返回此页重试。$MANUAL_ADD"
        }
    }

    /** OEM permissions have no standard runtime dialog. Fall back to the public app details page. */
    fun openPermissionSettings(context: Context): Boolean {
        val intents = mutableListOf<Intent>()
        if (Build.MANUFACTURER.equals("xiaomi", true) || Build.MANUFACTURER.equals("redmi", true)) {
            intents += Intent("miui.intent.action.APP_PERM_EDITOR")
                .setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"))
                .putExtra("extra_pkgname", context.packageName)
        }
        intents += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        for (intent in intents) {
            try { context.startActivity(intent); return true }
            catch (_: android.content.ActivityNotFoundException) { /* Try the public fallback. */ }
            catch (_: SecurityException) { /* Some OEM permission activities are private. */ }
        }
        return false
    }
}
