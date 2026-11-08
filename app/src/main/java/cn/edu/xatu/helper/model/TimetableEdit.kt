package cn.edu.xatu.helper.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

fun Course.occurrenceId(): String = localId ?: listOf(key, name, teacher, room, day.toString(), period.toString()).joinToString("") { "${it.length}:$it" }
fun teachingWeek(date: LocalDate, monday: LocalDate): Int = Math.floorDiv(ChronoUnit.DAYS.between(monday, date), 7).toInt() + 1

data class WeekAnchor(val week: Int? = null, val date: String? = null) {
    fun resolve(monday: LocalDate): Int = date?.let { teachingWeek(LocalDate.parse(it), monday) } ?: requireNotNull(week)
}
data class ManualCourse(val name: String, val teacher: String, val room: String, val days: List<Int>, val start: Int, val end: Int,
    val firstWeek: Int = 1, val lastWeek: Int = 1, val firstDate: String? = null, val lastDate: String? = null) {
    fun weeks(day: Int, monday: LocalDate): List<Int> = (1..30).filter { week ->
        if (firstDate != null && lastDate != null) {
            val date = monday.plusDays(((week - 1) * 7 + day - 1).toLong())
            date >= LocalDate.parse(firstDate) && date <= LocalDate.parse(lastDate)
        } else week in firstWeek..lastWeek
    }
}
data class EditSource(val ids: List<String>, val name: String, val key: String, val anchors: List<WeekAnchor>, val replacements: Map<Int, List<String>> = emptyMap())
data class EditDestination(val day: Int, val start: Int, val room: String)
enum class EditKind { ADD, MOVE, REMOVE }
data class TimetableEdit(val id: String = UUID.randomUUID().toString(), val kind: EditKind, val added: ManualCourse? = null,
    val source: EditSource? = null, val destination: EditDestination? = null) {
    val prefix: String get() = "local-$id|"
    val title: String get() = "${when(kind) { EditKind.ADD -> "添加"; EditKind.MOVE -> "修改"; EditKind.REMOVE -> "移除" }} · ${added?.name ?: source?.name.orEmpty()}"
}
data class EditedTimetable(val table: Timetable, val pending: Map<String, List<Int>>)

fun editTimetable(base: Timetable, edits: List<TimetableEdit>): EditedTimetable {
    if (base.kind != TableKind.STUDENT || edits.isEmpty()) return EditedTimetable(base, emptyMap())
    val monday = LocalDate.parse(base.monday)
    val courses = base.courses.associateBy { it.occurrenceId() }.toMutableMap()
    val pending = mutableMapOf<String, MutableList<Int>>()
    fun insert(course: Course) {
        val id = course.occurrenceId()
        courses[id] = course.copy(weeks = (courses[id]?.weeks.orEmpty() + course.weeks).distinct().sorted())
    }
    edits.forEach { edit ->
        if (edit.kind == EditKind.ADD) {
            val added = requireNotNull(edit.added)
            require(added.name.isNotBlank() && added.days.isNotEmpty() && added.days.all { it in 1..7 })
            if (added.start !in 1..base.unitCount || added.end !in added.start..base.unitCount) {
                pending[edit.id] = added.days.flatMap { added.weeks(it, monday) }.distinct().sorted().toMutableList()
                return@forEach
            }
            added.days.distinct().forEach { day ->
                val weeks = added.weeks(day, monday)
                if (weeks.isNotEmpty()) (added.start..added.end).forEach { period ->
                    insert(Course("local-${edit.id}", added.name, added.teacher, added.room, day, period, weeks,
                        localId = "${edit.prefix}$day|$period", fixedDates = added.firstDate != null))
                }
            }
        } else {
            val source = requireNotNull(edit.source)
            val target = if (edit.kind == EditKind.MOVE) requireNotNull(edit.destination) else null
            require(source.ids.isNotEmpty() && source.anchors.isNotEmpty())
            source.anchors.forEachIndexed { index, anchor ->
                val week = anchor.resolve(monday)
                if (week !in 1..30) return@forEachIndexed
                val ids = source.replacements[index] ?: source.ids
                val matches = ids.mapNotNull { courses[it]?.takeIf { course -> week in course.weeks } }.sortedBy { it.period }
                if (matches.size != ids.size || ids.distinct().size != ids.size ||
                    (target != null && (target.day !in 1..7 || target.start !in 1..base.unitCount || target.start + matches.size - 1 > base.unitCount))) {
                    pending.getOrPut(edit.id) { mutableListOf() }.add(week)
                    return@forEachIndexed
                }
                matches.forEachIndexed { offset, course ->
                    val remaining = course.weeks - week
                    if (remaining.isEmpty()) courses.remove(course.occurrenceId()) else courses[course.occurrenceId()] = course.copy(weeks = remaining)
                    if (target != null) insert(course.copy(day = target.day, period = target.start + offset, room = target.room, weeks = listOf(week), localId = "${edit.prefix}$offset"))
                }
            }
        }
    }
    return EditedTimetable(base.copy(courses = courses.values.sortedWith(compareBy({ it.day }, { it.period }, { it.occurrenceId() }))), pending)
}

fun sourceFor(table: Timetable, block: CourseBlock, weeks: List<Int>): EditSource {
    fun matches(week: Int) = table.courses.filter { it.key == block.key && it.name == block.name && it.teacher == block.teacher && it.room == block.room &&
        it.day == block.day && it.period in block.start..block.end && week in it.weeks }.sortedBy { it.period }
    require(weeks.isNotEmpty() && weeks.all { week ->
        week in 1..30 && blocksFor(table.courses, week).any { it.key == block.key && it.day == block.day && it.start == block.start && it.end == block.end && it.room == block.room && it.teacher == block.teacher }
    }) { "课程安排已变化，请重新选择课程。" }
    val initial = matches(weeks.first())
    val monday = LocalDate.parse(table.monday)
    return EditSource(initial.map { it.occurrenceId() }, block.name, block.key, weeks.map { week ->
        if (initial.any { it.fixedDates }) WeekAnchor(date = monday.plusDays(((week - 1) * 7 + block.day - 1).toLong()).toString()) else WeekAnchor(week = week)
    }, weeks.mapIndexed { index, week -> index to matches(week).map { it.occurrenceId() } }.toMap())
}

data class TimetableConflict(val name: String, val weeks: List<Int>)
fun timetableConflicts(after: Timetable, edit: TimetableEdit): List<TimetableConflict> {
    val added = after.courses.filter { it.localId?.startsWith(edit.prefix) == true }
    val others = after.courses.filter { it.localId?.startsWith(edit.prefix) != true }
    return others.groupBy { it.key to it.name }.values.mapNotNull { entries ->
        val weeks = entries.flatMap { other -> added.filter { it.day == other.day && it.period == other.period }.flatMap { it.weeks.intersect(other.weeks.toSet()) } }.distinct().sorted()
        if (weeks.isEmpty()) null else TimetableConflict(entries.first().name, weeks)
    }
}
