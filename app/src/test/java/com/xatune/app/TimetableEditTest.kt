package com.xatune.app

import com.xatune.app.model.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TimetableEditTest {
    private val monday = LocalDate.of(2026, 9, 7)
    private fun lesson(period: Int, weeks: List<Int> = listOf(2, 3), room: String = "教1") = Course("EN", "英语", "教师", room, 3, period, weeks)
    private fun table(courses: List<Course>) = Timetable("a", Semester("s", "学期"), TableKind.STUDENT, courses, 12, monday.toString(), 1)
    @Test fun dateRangesFollowCalendarWhileTeachingWeeksStayFixed() {
        val base = table(emptyList())
        val dated = TimetableEdit(kind = EditKind.ADD, added = ManualCourse("实验", "", "", listOf(1,3,5), 3, 4,
            firstDate = "2026-09-15", lastDate = "2026-09-23"))
        val weeks = TimetableEdit(kind = EditKind.ADD, added = ManualCourse("选修", "", "", listOf(3), 4, 4, 2, 3))
        val result = editTimetable(base, listOf(dated, weeks)).table
        assertEquals(listOf(3), result.courses.first { it.day == 1 }.weeks)
        assertEquals(listOf(2,3), result.courses.first { it.day == 3 && it.key == "local-${dated.id}" }.weeks)
        assertEquals(listOf(2), result.courses.first { it.day == 5 }.weeks)
        val shifted = editTimetable(base.copy(monday = monday.plusWeeks(1).toString()), listOf(dated, weeks)).table
        assertEquals(listOf(1,2), shifted.courses.first { it.day == 3 && it.key == "local-${dated.id}" }.weeks)
        assertEquals(listOf(2,3), shifted.courses.first { it.key == "local-${weeks.id}" }.weeks)
    }
    @Test fun movesAndRemovalsAreScopedAndSurviveSerializationAndRefresh() {
        val base = table(listOf(lesson(3), lesson(4)))
        val block = blocksFor(base.courses, 3).single()
        val move = TimetableEdit(kind = EditKind.MOVE, source = sourceFor(base, block, listOf(3)), destination = EditDestination(4,5,"教3-201"))
        val encoded = Gson().toJson(listOf(move))
        val saved = Gson().fromJson(encoded, Array<TimetableEdit>::class.java).toList()
        val refreshed = base.copy(courses = base.courses + Course("MATH", "数学", "", "", 4, 5, listOf(3)))
        val result = editTimetable(refreshed, saved)
        assertTrue(result.pending.isEmpty())
        assertEquals(listOf(2), result.table.courses.first { it.localId == null && it.period == 3 }.weeks)
        val moved = blocksFor(result.table.courses,3).first { it.key == "EN" }
        assertEquals(4, moved.day); assertEquals(5, moved.start); assertEquals(6, moved.end); assertEquals("教3-201", moved.room)
        assertEquals(listOf(3), timetableConflicts(result.table, move).single().weeks)
        val remove = TimetableEdit(kind = EditKind.REMOVE, source = sourceFor(result.table, moved, listOf(3)))
        assertFalse(editTimetable(base, saved + remove).table.courses.any { 3 in it.weeks })
        assertEquals(base, editTimetable(base, emptyList()).table)
        assertEquals(base.copy(kind = TableKind.CLASS), editTimetable(base.copy(kind = TableKind.CLASS), saved).table)
    }
    @Test fun changedSourcesWaitForConfirmationAndCanBeReapplied() {
        val base = table(listOf(lesson(3), lesson(4)))
        val move = TimetableEdit(kind = EditKind.MOVE, source = sourceFor(base, blocksFor(base.courses,3).single(), listOf(3)), destination = EditDestination(4,5,""))
        val changed = table(listOf(lesson(3, room = "教2"), lesson(4, room = "教2")))
        val result = editTimetable(changed, listOf(move))
        assertEquals(listOf(3), result.pending[move.id]); assertEquals(changed, result.table)
        val replacement = sourceFor(changed, blocksFor(changed.courses,3).single(), listOf(3)).ids
        val repaired = move.copy(source = move.source!!.copy(replacements = mapOf(0 to replacement)))
        assertTrue(editTimetable(changed, listOf(repaired)).pending.isEmpty())
        val partial = editTimetable(base.copy(courses = listOf(lesson(3))), listOf(move))
        assertEquals(listOf(3), partial.pending[move.id])
        assertEquals(listOf(2,3), partial.table.courses.single().weeks)
    }
    @Test fun shortenedSchoolDayLeavesAddedCoursePendingWithoutBreakingRefresh() {
        val base = table(emptyList())
        val add = TimetableEdit(kind = EditKind.ADD, added = ManualCourse("晚课", "", "", listOf(1), 11, 12, 1, 3))
        val result = editTimetable(base.copy(unitCount = 10), listOf(add))
        assertTrue(result.table.courses.isEmpty())
        assertEquals(listOf(1,2,3), result.pending[add.id])
    }
    @Test fun fixedDateRemovalFollowsCalibrationAndAlternatingPeriodsStaySeparate() {
        val base = table(emptyList())
        val add = TimetableEdit(kind = EditKind.ADD, added = ManualCourse("实验", "", "", listOf(3), 3,4, firstDate = "2026-09-16", lastDate = "2026-09-23"))
        val added = editTimetable(base, listOf(add)).table
        val remove = TimetableEdit(kind = EditKind.REMOVE, source = sourceFor(added, blocksFor(added.courses,2).single(), listOf(2)))
        val shifted = editTimetable(base.copy(monday = monday.plusWeeks(1).toString()), listOf(add, remove)).table
        assertEquals(listOf(2), shifted.courses.first().weeks)
        val alternating = table(listOf(lesson(3), lesson(4, listOf(3))))
        assertThrows(IllegalArgumentException::class.java) { sourceFor(alternating, blocksFor(alternating.courses,3).single(), listOf(2,3)) }
    }
}
