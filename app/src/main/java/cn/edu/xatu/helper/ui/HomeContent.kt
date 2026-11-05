package cn.edu.xatu.helper.ui

import cn.edu.xatu.helper.model.*
import java.time.LocalDate
import java.time.LocalTime

/** Keep wording and time boundaries aligned with iOS/Watch CampusGreeting. */
internal fun campusGreeting(time: LocalTime): String = when (time.hour * 60 + time.minute) {
    in 300 until 690 -> "早，"
    in 690 until 810 -> "吃饭没？"
    in 810 until 1080 -> "下午好，"
    in 1080 until 1350 -> "晚上好，"
    else -> "晚安。"
}

internal data class HomeSchedule(val title: String, val date: LocalDate, val courses: List<CourseBlock>, val remaining: Int, val active: String?, val next: String?, val tomorrow: Boolean)

internal fun homeSchedule(table: Timetable?, currentSemester: Boolean, date: LocalDate, time: LocalTime): HomeSchedule {
    val today = if (currentSemester) coursesOn(table, date) else emptyList()
    fun start(block: CourseBlock) = LocalTime.parse(Periods.range(block).substringBefore('–'))
    fun end(block: CourseBlock) = LocalTime.parse(Periods.range(block).substringAfter('–'))
    val active = today.firstOrNull { start(it) <= time && time < end(it) }
    val remaining = today.filter { start(it) > time }
    val tomorrow = active == null && remaining.isEmpty()
    val shownDate = if (tomorrow) date.plusDays(1) else date
    val displayed = if (tomorrow) { if (currentSemester) coursesOn(table, shownDate) else emptyList() } else today.filter { end(it) > time }
    val title = when {
        tomorrow -> if (displayed.isEmpty()) "明天没有课，好好休息下吧" else "明天要上这些课："
        active != null || remaining.size != today.size -> "今天还有这些课："
        else -> "今天要上这些课："
    }
    return HomeSchedule(title, shownDate, displayed, remaining.size + if (active != null) 1 else 0, active?.id, if (active == null) remaining.firstOrNull()?.id else null, tomorrow)
}
