package cn.edu.xatu.helper

import cn.edu.xatu.helper.data.*
import cn.edu.xatu.helper.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class CoreTest {
    private fun activity(name: String = "课程(001)", room: String = "教1-101", mask: String = "011", positions: String = "index=0*unitCount+0; index=0*unitCount+1;"): String = """
        var actTeachers = [{id:1,name:"示例教师",lab:false}];
        activity = new TaskActivity(a.join(','), b.join(','), "42(001)", "$name", "12", "$room", "$mask", null);
        $positions
    """
    private fun page(body: String) = "var table0 = new CourseTable(2026,84); var unitCount = 12; $body"
    @Test fun weekMaskUsesOneBasedIndexAndMergesDuplicateWeeks() {
        val result = TimetableParser.parse(page(activity(mask = "0101") + activity(mask = "0011")))
        assertEquals(2, result.rawCount); assertEquals(2, result.courses.size)
        assertEquals(listOf(1, 2, 3), result.courses.first().weeks)
        assertEquals("课程", result.courses.first().name)
        assertEquals("示例教师", result.courses.first().teacher)
        assertEquals(1, result.courses.first().day)
    }
    @Test fun nestedCommasParenthesesAndEscapedQuotesAreHandled() {
        val table = TimetableParser.parse(page(activity(name = "课程，实验(一)(001)")))
        assertEquals("课程，实验(一)", table.courses.first().name)
    }
    @Test fun onlineAndPlaceholderActivitiesAreFiltered() {
        val result = TimetableParser.parse(page(activity(room = "线上教室（平台修读）") + activity(name = "实践环节(002)")))
        assertEquals(2, result.rawCount); assertTrue(result.courses.isEmpty())
    }
    @Test fun legitimateEmptySemesterIsDifferentFromBrokenResponse() {
        assertTrue(TimetableParser.parse(page("")).courses.isEmpty())
        assertThrows(ParseFailure::class.java) { TimetableParser.parse("<html>服务器维护</html>") }
        assertThrows(ParseFailure::class.java) { TimetableParser.parse(page("new TaskActivity(broken")) }
    }
    @Test fun adjacentPeriodsInDifferentWeeksAreNeverMerged() {
        val courses = listOf(Course("a", "课程", "", "教1", 1, 1, listOf(1)), Course("a", "课程", "", "教1", 1, 2, listOf(2)))
        assertEquals(1, blocksFor(courses, 1).single().end)
        assertEquals(2, blocksFor(courses, 2).single().start)
    }
    @Test fun overlappingCoursesReceiveSeparateLanes() {
        fun block(key: String, start: Int, end: Int) = CourseBlock(key, key, "", "", 1, start, end, listOf(1))
        val placed = placeBlocks(listOf(block("a", 1, 3), block("b", 2, 4), block("c", 5, 6)))
        assertEquals(2, placed.first().count); assertNotEquals(placed[0].lane, placed[1].lane)
        assertEquals(1, placed.last().count)
    }
    @Test fun datesCrossYearsAndUseCalendarMonday() {
        val start = LocalDate.parse("2026-12-28")
        assertEquals(1, weekOf(start, LocalDate.parse("2027-01-03")))
        assertEquals(2, weekOf(start, LocalDate.parse("2027-01-04")))
        assertEquals(start, mondayOf(LocalDate.parse("2027-01-03")))
        assertEquals(1, weekOf(start, start.minusDays(1)))
    }
    @Test fun semesterObjectsAndHtmlFallbackParse() {
        val list = EamsParser.semesters("""{semesters:{y:[{id:101,schoolYear:'2026-2027',name:'1',startDate:'2026-09-07'},{id:99,schoolYear:'2025-2026',name:'2',},]}}""")
        assertEquals("101", list.first().id); assertEquals("2026-09-07", list.first().startDate)
        assertEquals("2026-2027学年1学期", list.first().name)
        assertEquals("12", EamsParser.semesters("<a val='12'>学期<span>1</span></a>").single().id)
    }
    @Test fun shellAndLoginDetection() {
        val shell = EamsParser.shell("""<div id='semesterBar7Semester'></div><script>semesterCalendar({value:"101"}); add("ids","111");add('ids','222');</script>""")
        assertEquals("111", shell.studentId); assertEquals("222", shell.classId); assertEquals("semesterBar7Semester", shell.tagId)
        assertThrows(SessionExpired::class.java) { EamsParser.shell("<input name='password'>") }
        assertTrue(SchoolNetwork.captchaRequired("var captchaSwitch = 'true';"))
        assertFalse(SchoolNetwork.captchaRequired("captchaSwitch=0;"))
        assertEquals(8, EamsParser.officialWeek("<span>8</span><i>/20</i>当前教学周"))
    }
    @Test fun casEncryptionIsCompatibleAndNeverFallsBackToPlaintext() {
        val salt = "0123456789abcdef"; val iv = "abcdefghijklmnop"; val prefix = "x".repeat(64)
        val encrypted = CasCrypto.encrypt("密码123", salt, prefix, iv)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(salt.toByteArray(), "AES"), IvParameterSpec(iv.toByteArray()))
        assertEquals(prefix + "密码123", String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), Charsets.UTF_8))
        assertThrows(IllegalArgumentException::class.java) { CasCrypto.encrypt("password", "bad") }
    }
    @Test fun teachingBuildingThreeUsesItsActualMorningSchedule() {
        assertEquals("10:10–11:40", Periods.range(CourseBlock("a", "", "", "教3-101", 1, 3, 4, listOf(1))))
        assertEquals("10:20–12:00", Periods.range(CourseBlock("a", "", "", "教1-101", 1, 3, 4, listOf(1))))
        assertEquals("10:20–12:00", Periods.range(CourseBlock("a", "", "", "教2-101", 1, 3, 4, listOf(1))))
        assertEquals("10:10/10:20", Periods.gridStartLabel(3))
        assertEquals("10:20", Periods.starts[2])
        assertEquals("09:15", Periods.gridStartLabel(2))
    }
    @Test fun sanitizedOriginalEamsScriptParses() {
        val html = javaClass.getResource("/eams-table.html")!!.readText()
        val parsed = TimetableParser.parse(html)
        assertEquals(12, parsed.unitCount)
        assertTrue(parsed.rawCount > 10); assertTrue(parsed.courses.size > 10)
        assertTrue(parsed.courses.none { "线上" in it.room || "实践环节" in it.name })
        assertTrue(parsed.courses.all { it.day in 1..7 && it.period in 1..12 && it.weeks.all { w -> w > 0 } })
    }
}
