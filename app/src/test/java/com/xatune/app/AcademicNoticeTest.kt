package com.xatune.app

import com.xatune.app.model.*
import org.junit.Assert.*
import org.junit.Test

class AcademicNoticeTest {
    private val exam = Exam("e1", "高等数学", "MATH", "期末", "2026-12-01 09:00", "教1-101", "1", "")
    private val grade = Grade("g1", "高等数学", "MATH", "5", "--", "", "term", emptyList())
    private fun grades(vararg values: Grade) = AcademicSnapshot(grades = GradePage(values.toList(), GradeSummary()))

    @Test fun firstSnapshotAndUnchangedDataDoNotNotify() {
        val snapshot = AcademicSnapshot(exams = listOf(exam))
        assertNull(academicNotice(AcademicKind.EXAMS, "term", null, snapshot))
        assertNull(academicNotice(AcademicKind.EXAMS, "term", snapshot, snapshot))
        assertNull(academicNotice(AcademicKind.GRADES, "all", null, grades(grade.copy(score = "90"))))
        assertNull(academicNotice(AcademicKind.GRADES, "term", grades(grade), grades(grade)))
    }
    @Test fun examTimeAndRoomChangesNotifyButSeatOnlyChangesDoNot() {
        val old = AcademicSnapshot(exams = listOf(exam))
        for (changed in listOf(exam.copy(time = "2026-12-02 09:00"), exam.copy(room = "教2-201"))) {
            val notice = academicNotice(AcademicKind.EXAMS, "old-term", old, AcademicSnapshot(exams = listOf(changed)))!!
            assertEquals("有新的考试安排", notice.title)
            assertEquals("高等数学", notice.message)
            assertEquals("old-term", notice.scope)
        }
        assertNull(academicNotice(AcademicKind.EXAMS, "term", old, AcademicSnapshot(exams = listOf(exam.copy(seat = "2")))))
    }
    @Test fun gradeNoticesIgnoreUnpublishedAndIncludeNewOrChangedScores() {
        assertEquals(0, gradeChanges(listOf(grade.copy(score = "90")), listOf(grade)))
        val notice = academicNotice(AcademicKind.GRADES, "all", grades(grade), grades(grade.copy(score = "90")))!!
        assertEquals("有新的成绩公布", notice.title)
        assertEquals("高等数学 90", notice.message)
        assertEquals("all", notice.scope)
        assertEquals(1, gradeChanges(listOf(grade.copy(score = "90")), listOf(grade.copy(score = "92"))))
        assertEquals(1, gradeChanges(listOf(grade), listOf(grade.copy(id = "new", score = "85"))))
    }
    @Test fun noticesSummarizeFirstTwoCoursesAndTotal() {
        val current = AcademicSnapshot(exams = listOf(exam, exam.copy(id = "e2", course = "大学英语"), exam.copy(id = "e3", course = "程序设计"), exam.copy(id = "e4", course = "大学物理")))
        val notice = academicNotice(AcademicKind.EXAMS, "term", AcademicSnapshot(exams = listOf(exam)), current)!!
        assertEquals("大学英语、程序设计 等 3 场", notice.message)
    }
}
