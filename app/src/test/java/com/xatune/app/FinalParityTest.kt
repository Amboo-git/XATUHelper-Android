package com.xatune.app

import com.xatune.app.model.*
import com.xatune.app.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinalParityTest {
    private val monday = LocalDate.of(2026, 12, 28)
    private val table = Timetable("private-account", Semester("private-semester", "私人学期"), TableKind.STUDENT,
        listOf(Course("private-code", "私人课程", "私人教师", "私人教室", 7, 1, listOf(1)),
            Course("b", "下周课程", "", "", 1, 1, listOf(2))), 12, monday.toString(), 123)

    @Test fun calendarConsumersDoNotClampDatesBeforeOrAfterTeachingRange() {
        assertTrue(coursesOn(table, monday.minusDays(1)).isEmpty())
        assertTrue(coursesOn(table, monday.plusWeeks(30)).isEmpty())
        assertTrue(coursesOn(null, monday).isEmpty())
        assertEquals("私人课程", coursesOn(table, monday.plusDays(6)).single().name)
        assertEquals("下周课程", coursesOn(table, monday.plusWeeks(1)).single().name)
    }
    @Test fun countdownIncludesSchoolOpeningAfterVacationStarts() {
        val calendar = SchoolCalendar("", "", "", "", "", listOf(
            SemesterSpan("第一学期", "2026-08-31", "2027-01-15"),
            SemesterSpan("第二学期", "2027-02-22", "2027-07-16")), 0, "2027-08-29")
        assertEquals("放寒假", nextCampusEvent(calendar, LocalDate.of(2027, 1, 15))?.title)
        assertEquals("第二学期开学", nextCampusEvent(calendar, LocalDate.of(2027, 1, 17))?.title)
        assertEquals("放暑假", nextCampusEvent(calendar, LocalDate.of(2027, 2, 23))?.title)
        assertNull(nextCampusEvent(calendar, LocalDate.of(2027, 8, 30)))
    }
    @Test fun examsMatchIosFuturePastAndUndatedOrdering() {
        fun exam(id: String, time: String) = Exam(id, "", "", "", time, "", "", "")
        val exams = listOf(exam("old", "2026-12-01 09:00"), exam("later", "2027-01-03 10:00"),
            exam("unknown", "待定"), exam("today-late", "2027-01-01 14:00"),
            exam("today-early", "2027-01-01 09:00"), exam("recent", "2026-12-31 09:00"))
        assertEquals(listOf("today-early", "today-late", "later", "recent", "old", "unknown"),
            orderedExams(exams, monday, LocalDate.of(2027, 1, 1)).map { it.id })
    }
    @Test fun searchesTrimWhitespaceAndCalibrationUsesThisCalendarWeek() {
        assertTrue(matchesCourse("程序设计", "CS01", "  程序  "))
        assertTrue(matchesCourse("程序设计", "CS01", " cs01 "))
        assertTrue(matchesCourse("程序设计", "CS01", "   "))
        assertFalse(matchesCourse("程序设计", "CS01", "物理"))
        assertEquals(LocalDate.of(2026, 11, 2), mondayForWeek(8, LocalDate.of(2026, 12, 27)))
    }
    @Test fun nestedPagesReturnToTheirOwningScreenAndExamsUseCurrentSemesterAnchor() {
        assertEquals("plan", parentPage("planWeb"))
        assertEquals("electricity", parentPage("charge"))
        assertEquals("settings", parentPage("notifications"))
        assertEquals("settings", parentPage("widgets"))
        assertNull(parentPage("settings"))
        val state = UiState(currentSemester = "current", selectedSemester = "historical", table = table,
            semesters = listOf(Semester("current", "", "2027-09-01")))
        assertEquals(LocalDate.of(2027, 9, 1), examAnchor(state))
    }
    @Test fun examCountdownMatchesIosAcrossDatesAndAcademicYears() {
        fun exam(time: String) = Exam("", "", "", "", time, "", "", "")
        val today = LocalDate.of(2026, 12, 31)
        val anchor = LocalDate.of(2026, 9, 1)
        assertEquals("还有 2 天", exam("2027-01-02 09:00").remainingText(anchor, today))
        assertEquals("今天", exam("2026-12-31 08:00").remainingText(anchor, today))
        assertEquals("", exam("2026-12-30 09:00").remainingText(anchor, today))
        assertEquals("", exam("待公布").remainingText(anchor, today))
        assertEquals("", exam("2027-02-30 09:00").remainingText(anchor, today))
        assertEquals("还有 2 天", exam("1月2日 09:00").remainingText(anchor, today))
        assertEquals("今天", exam("2027-01-02 09:00").remainingText(anchor, today.plusDays(2)))
        assertEquals("", exam("2027-01-02 09:00").remainingText(anchor, today.plusDays(3)))
        assertEquals("还有 2 天", exam("2028-03-01 09:00").remainingText(anchor, LocalDate.of(2028, 2, 28)))
    }
    @Test fun diagnosticsContainCountersWithoutIdentityContentOrServerErrors() {
        val state = UiState(account = "private-account", table = table, semesters = listOf(table.semester),
            webAccount = "private-web-account", error = "password=cookie=token=secret",
            academic = mapOf(academicKey(AcademicKind.GRADES, "private-semester") to AcademicUi(error = "secret")))
        val report = diagnosticSummary(state)
        listOf("private-", "私人", "password", "cookie", "token", "secret").forEach { assertFalse(report.contains(it)) }
        assertTrue(report.contains("课程节次：2"))
        assertTrue(report.contains("成绩缓存：0；加载中：0；失败：1"))
    }
    @Test fun remindersRespectSevenDayBoundaryAndCustomExamTimes() {
        val now = monday.atTime(7, 0).atZone(SchoolZone)
        val exams = listOf(Exam("e", "课程", "", "", "2027-01-05 09:00–11:00", "", "", ""))
        val planned = ReminderPlanner.plan(null, exams, ReminderSettings(exams = true, eveMinutes = 6 * 60), now)
        // Only the previous-day reminder falls before the seven-day horizon.
        assertEquals(1, planned.size)
        assertEquals(monday.plusDays(7).atTime(6, 0).atZone(SchoolZone).toInstant().toEpochMilli(), planned.single().at)
    }
}
