package cn.edu.xatu.helper

import cn.edu.xatu.helper.model.*
import cn.edu.xatu.helper.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HomeContentTest {
    @Test fun greetingMatchesEveryIosBoundaryIncludingMidnight() {
        for ((time, expected) in listOf("00:00" to "晚安。", "04:59" to "晚安。", "05:00" to "早，", "11:29" to "早，", "11:30" to "吃饭没？", "13:29" to "吃饭没？", "13:30" to "下午好，", "17:59" to "下午好，", "18:00" to "晚上好，", "22:29" to "晚上好，", "22:30" to "晚安。", "23:59" to "晚安。")) assertEquals(time, expected, campusGreeting(LocalTime.parse(time)))
    }
    private val date = LocalDate.parse("2026-11-02")
    private val table = Timetable("fixture", Semester("current","current"), TableKind.STUDENT,
        listOf(Course("a","早课","教师","教1",1,1,listOf(1)), Course("b","午后课","教师","教1",1,5,listOf(1)), Course("c","明日课程","教师","教1",2,1,listOf(1))),12,date.toString(),0)
    @Test fun endedCoursesAreHiddenAndActiveOrNextCourseIsBadged() {
        val early = homeSchedule(table,true,date,LocalTime.of(7,0))
        assertEquals("今天要上这些课：",early.title)
        assertEquals(2,early.remaining)
        assertEquals(early.courses.first().id,early.next)
        val active = homeSchedule(table,true,date,LocalTime.of(8,30))
        assertNotNull(active.active)
        assertNull(active.next)
        val later = homeSchedule(table,true,date,LocalTime.of(11,0))
        assertEquals(listOf("午后课"),later.courses.map { it.name })
        assertEquals("今天还有这些课：",later.title)
    }
    @Test fun afterLastClassTomorrowIsShownAndMidnightUsesNewDay() {
        val night = homeSchedule(table,true,date,LocalTime.of(23,0))
        assertTrue(night.tomorrow)
        assertEquals(date.plusDays(1),night.date)
        assertEquals(listOf("明日课程"),night.courses.map { it.name })
        val midnight = homeSchedule(table,true,date.plusDays(1),LocalTime.MIDNIGHT)
        assertFalse(midnight.tomorrow)
        assertEquals(date.plusDays(1),midnight.date)
        assertEquals(listOf("明日课程"),midnight.courses.map { it.name })
        val historical = homeSchedule(table,false,date,LocalTime.of(7,0))
        assertTrue(historical.courses.isEmpty())
        assertEquals(0,historical.remaining)
    }
}
