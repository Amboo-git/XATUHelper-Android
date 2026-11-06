package cn.edu.xatu.helper.platform

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.widget.RemoteViews
import cn.edu.xatu.helper.*
import cn.edu.xatu.helper.model.*
import kotlinx.coroutines.*
import java.time.*
import java.time.temporal.ChronoUnit

object CampusWidgets {
    suspend fun publish(context: Context, account: String, table: Timetable?) {
        val app = context.applicationContext as HelperApplication
        val manager = AppWidgetManager.getInstance(context)
        val options = context.getSharedPreferences("widget_options", Context.MODE_PRIVATE)
        val now = ZonedDateTime.now(SchoolZone)
        for ((type, provider) in listOf("course" to CourseWidget::class.java, "electricity" to ElectricityWidget::class.java, "holiday" to HolidayWidget::class.java)) {
            for (id in manager.getAppWidgetIds(ComponentName(context, provider))) {
                val view = RemoteViews(context.packageName, R.layout.widget_campus)
                var title = when (type) { "course" -> "课程"; "electricity" -> "宿舍电费"; else -> "假期倒计时" }
                var body = "登录后查看校园信息"
                var subtitle = "点击打开 XATune"
                var action = "打开"
                val page = when (type) { "course" -> "timetable"; "electricity" -> "electricity"; else -> "calendar" }
                if (account.isNotBlank()) when (type) {
                    "course" -> {
                        val auto = options.getBoolean("tomorrow:$id", false)
                        val today = coursesOn(table, now.toLocalDate()).filter { LocalTime.parse(Periods.range(it).substringAfter('–')) > now.toLocalTime() }
                        val tomorrow = auto && today.isEmpty()
                        val displayed = if (tomorrow) coursesOn(table, now.toLocalDate().plusDays(1)) else today
                        title = if (tomorrow) "明日课程" else "今日课程"
                        body = if (table == null) "请先同步当前学期的学生课表" else displayed.take(3).joinToString("\n") { "${Periods.range(it)} ${it.name}\n${it.room}" }.ifBlank { if (tomorrow) "明天没有课程安排" else "今天的课程已结束或暂无课程" }
                        val displayedDate = if (tomorrow) now.toLocalDate().plusDays(1) else now.toLocalDate()
                        subtitle = if (table == null) "点击打开课程表" else "${displayedDate.monthValue}/${displayedDate.dayOfMonth} · 本地课表"
                        action = if (auto) "上完看明天：开" else "上完看明天：关"
                    }
                    "electricity" -> {
                        val electric = app.campusRepository.electricity(account)
                        body = electric?.let { "${it.balance} 度" } ?: "请在 App 中选择宿舍"
                        subtitle = electric?.let { it.dorm.building.name + " " + it.dorm.room.name + " · " + Instant.ofEpochMilli(it.checkedAt).atZone(SchoolZone).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")) } ?: "点击绑定宿舍"
                        options.getString("electricError", null)?.let { subtitle = it }
                        action = "刷新余额"
                    }
                    else -> {
                        val calendar = app.campusRepository.calendar()
                        val next = nextCampusEvent(calendar, now.toLocalDate())
                        val active = nextBreak(calendar, now.toLocalDate())?.takeIf { it.start <= now.toLocalDate() }
                        if (next != null) {
                            val days = ChronoUnit.DAYS.between(now.toLocalDate(), next.date)
                            body = "${next.title}：${if (days == 0L) "今天" else "$days 天"}"
                            subtitle = "${next.date} · ${active?.let { "${it.title}中 · " }.orEmpty()}校历识别供参考"
                        } else if (active != null) {
                            body = "${active.title}中"
                            subtitle = "至 ${active.end} · 校历识别供参考"
                        } else { body = "获取校历后显示假期"; subtitle = "点击查看官方校历" }
                    }
                }
                view.setTextViewText(R.id.widget_title, title)
                view.setTextViewText(R.id.widget_body, body)
                view.setTextViewText(R.id.widget_subtitle, subtitle)
                view.setTextViewText(R.id.widget_action, action)
                val open = PendingIntent.getActivity(context, id, SystemCoordinator.launchIntent(context, page).setData(android.net.Uri.parse("xatu://widget/$id")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                view.setOnClickPendingIntent(R.id.widget_root, open)
                if (type == "course" || type == "electricity") {
                    val intent = Intent(context, provider).setAction(if (type == "course") "cn.edu.xatu.helper.TOGGLE_TOMORROW" else "cn.edu.xatu.helper.REFRESH_ELECTRICITY").putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    view.setOnClickPendingIntent(R.id.widget_action, PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                } else view.setOnClickPendingIntent(R.id.widget_action, open)
                manager.updateAppWidget(id, view)
            }
        }
    }
}
open class CampusWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetPinning.PINNED) {
            context.getSharedPreferences("widget_options", Context.MODE_PRIVATE).edit()
                .putLong(WidgetPinning.CONFIRMED, System.currentTimeMillis()).apply()
            update(context)
            return
        }
        if (intent.action == "cn.edu.xatu.helper.REFRESH_ELECTRICITY") { SystemCoordinator.refreshElectricity(context); return }
        if (intent.action == "cn.edu.xatu.helper.TOGGLE_TOMORROW") {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (id < 0) return
            val prefs = context.getSharedPreferences("widget_options", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("tomorrow:$id", !prefs.getBoolean("tomorrow:$id", false)).apply()
            update(context); return
        }
        super.onReceive(context, intent)
    }
    private fun update(context: Context) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { SystemCoordinator.synchronize(context) } finally { result.finish() } }
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { update(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) { update(context) }
    override fun onDeleted(context: Context, ids: IntArray) { context.getSharedPreferences("widget_options", Context.MODE_PRIVATE).edit().apply { ids.forEach { remove("tomorrow:$it") } }.apply() }
}
class CourseWidget : CampusWidgetProvider()
class ElectricityWidget : CampusWidgetProvider()
class HolidayWidget : CampusWidgetProvider()
