package cn.edu.xatu.helper

import cn.edu.xatu.helper.data.*
import cn.edu.xatu.helper.model.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CampusTest {
    private class Jar : CookieJar {
        val values = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { cookies.forEach { incoming -> values.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }; if (incoming.expiresAt > System.currentTimeMillis()) values.add(incoming) } }
        override fun loadForRequest(url: HttpUrl) = values.filter { it.matches(url) }
    }
    @Test fun electricityUsesDecimalMoneyNotCumulativeConsumption() {
        assertEquals("53.87", balanceAmount("9183.18", "9237.05"))
        assertEquals("0.20", balanceAmount("0.10", "0.30"))
        assertEquals("-1.00", balanceAmount("12", "11"))
        assertEquals("5.00", balanceAmount("5", null))
        assertThrows(IllegalArgumentException::class.java) { balanceAmount("bad", "2") }
    }
    @Test fun usageNeverTreatsRechargesOrMissingDaysAsConsumption() {
        assertEquals("2.50", dailyDeduction(DailyBalance("2026-11-01", "10.00"), DailyBalance("2026-11-02", "7.50"))!!.toPlainString())
        assertNull(dailyDeduction(DailyBalance("2026-11-01", "10"), DailyBalance("2026-11-02", "50")))
        assertNull(dailyDeduction(DailyBalance("2026-11-01", "10"), DailyBalance("2026-11-03", "7")))
    }
    @Test fun tokenMayBeInQueryOrSpaFragment() {
        assertEquals("abc+123", PayNetwork.tokenFromUrl("https://example/cas/#/middle_page?token=abc%2B123&idserial=1"))
        assertEquals("token", PayNetwork.tokenFromUrl("https://example/?token=token"))
        assertNull(PayNetwork.tokenFromUrl("https://example/?ticket=secret"))
    }
    @Test fun paymentApiRenewsOnceAndOnlyCallsReadOnlyEndpoints() {
        MockWebServer().use { server ->
            server.start()
            val root = server.url("/").toString()
            val network = PayNetwork(Jar(), root, server.url("/auth").toString())
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", root + "#/middle_page?token=first"))
            server.enqueue(MockResponse().setBody("""{"messageCode":"50014"}"""))
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", root + "#/middle_page?token=second"))
            server.enqueue(MockResponse().setBody("""{"messageCode":"0","data":[{"id":"p","projectName":"电费","proModelUrl":"electric"}]}"""))
            assertEquals("p", network.projects().single().id)
            assertTrue(server.takeRequest().path!!.startsWith("/auth?service="))
            val old = server.takeRequest(); assertEquals("first", old.getHeader("X-Token")); assertEquals("GET", old.method)
            server.takeRequest()
            val fresh = server.takeRequest(); assertEquals("second", fresh.getHeader("X-Token")); assertEquals("/api/pay/project/getAllProjectList", fresh.path)
        }
    }
    @Test fun dormRequestsCarryAllHierarchyIdsAndBalanceIsCalculated() {
        MockWebServer().use { server ->
            server.start(); val jar = Jar(); val root = server.url("/")
            jar.saveFromResponse(root, listOf(Cookie.Builder().name("datalook_reimbursement_token").value("fixture").hostOnlyDomain(root.host).path("/").build()))
            val net = PayNetwork(jar, root.toString(), server.url("/auth").toString())
            server.enqueue(MockResponse().setBody("""{"messageCode":0,"data":[{"oddl":9183.18,"suml":9237.05}]}"""))
            val dorm = Dorm(CampusOption("p","P"),CampusOption("a","A"),CampusOption("b","B"),CampusOption("r","R"))
            assertEquals("53.87", net.balance(dorm))
            val request = server.takeRequest(); val body = request.body.readUtf8()
            assertTrue(body.contains("\"projectId\":\"p\"")); assertTrue(body.contains("\"roomid\":\"r\""))
            assertEquals("/api/pay/web/payEleCostController/querySydl", request.path)
        }
    }
    @Test fun calendarSelectsOfficialContentAndImageInsteadOfLogo() {
        val page = CalendarParser.latest("<a href='/info/1001/2001.htm'>2026-2027学年校历</a>")
        assertEquals("https://jwcw.xatu.edu.cn/info/1001/2001.htm", page)
        assertEquals("https://jwcw.xatu.edu.cn/__local/calendar.png", CalendarParser.image("<img src='/logo.png'><img src='/__local/calendar.png'>", page))
    }
    @Test fun ocrRangesInferCrossYearAndRejectInvalidDates() {
        val spans = CalendarParser.semesters("第一学期2026年9月7日至1月20日；第二学期2027年2月20日—7月10日")
        assertEquals(2, spans.size); assertEquals("2027-01-20", spans[0].end)
        assertTrue(CalendarParser.semesters("2026年2月30日至7月1日").isEmpty())
        assertTrue(CalendarParser.semesters("国庆节2026年10月1日至10月7日").isEmpty())
        val calendar = SchoolCalendar("", "", "", "", "", spans, 0)
        assertEquals("2027-01-21", nextBreak(calendar, LocalDate.parse("2026-11-01"))!!.start.toString())
    }
    @Test fun summerRangeUsesImageEndAndRemainsActiveThroughLastDay() {
        val spans = CalendarParser.semesters("2026年8月31日－2027年1月15日；2027年3月1日—2027年7月16日")
        val calendar = SchoolCalendar("", "", "", "", "", spans, 0, "2027-08-29")
        assertEquals(listOf(SchoolBreak("寒假", LocalDate.parse("2027-01-16"), LocalDate.parse("2027-02-28")), SchoolBreak("暑假", LocalDate.parse("2027-07-17"), LocalDate.parse("2027-08-29"))), schoolBreaks(calendar))
        assertEquals("暑假", nextBreak(calendar, LocalDate.parse("2027-08-29"))?.title)
        assertNull(nextBreak(calendar, LocalDate.parse("2027-08-30")))
        assertNull(schoolBreaks(calendar.copy(coverageEnd = null)).last().end)
        assertNull(nextBreak(calendar.copy(coverageEnd = null), LocalDate.parse("2027-08-01")))
    }
    @Test fun gridEndIgnoresWeekNumbersAndFooterDates() {
        fun cell(text: String, x: Int, y: Int) = CalendarParser.Cell(text, x, y, x + 20, y + 20)
        val spans = listOf(SemesterSpan("第二学期", "2027-03-01", "2027-07-16"))
        val grid = listOf(cell("7月", 600, 100), cell("8月", 800, 100), cell("28", 920, 260), cell("22", 860, 300), cell("29", 920, 300), cell("周次", 20, 340), cell("26", 920, 340), cell("2027年7月16日", 500, 400))
        assertEquals("2027-08-29", CalendarParser.coverageEnd(grid, spans))
        assertNull(CalendarParser.coverageEnd(grid.filter { it.text != "周次" }, spans))
        assertEquals("https://jwcw.xatu.edu.cn/original.jpg", CalendarParser.image("<img src='/__local/thumb.jpg' orisrc='/original.jpg'>", CalendarParser.index))
    }
    private val now = ZonedDateTime.of(2026, 11, 2, 7, 0, 0, 0, SchoolZone)
    private fun table(kind: TableKind = TableKind.STUDENT): Timetable = Timetable("fixture", Semester("1","S"), kind, listOf(Course("c","课程","老师","教1",1,1,listOf(1)),Course("c","课程","老师","教1",1,2,listOf(2))),12,"2026-11-02",0)
    @Test fun reminderMergesOnlyActualPeriodsAndRespectsCurrentWeek() {
        val result = ReminderPlanner.plan(table(), emptyList(), ReminderSettings(classes = true), now)
        assertEquals(1, result.size)
        assertEquals(now.withHour(7).withMinute(50).toInstant().toEpochMilli(), result.single().at)
        assertTrue(result.single().body.startsWith("08:20–09:05"))
    }
    @Test fun classTablesAndDisabledTypesNeverScheduleLessons() {
        assertTrue(ReminderPlanner.plan(table(TableKind.CLASS), emptyList(), ReminderSettings(classes = true), now).isEmpty())
        assertTrue(ReminderPlanner.plan(table(), emptyList(), ReminderSettings(), now).isEmpty())
    }
    @Test fun examHasThreeIndependentFutureReminders() {
        val exam = Exam("e","考试课","c","","2026-11-04 09:00–11:00","教1","12","")
        val planned = ReminderPlanner.plan(null, listOf(exam), ReminderSettings(exams = true), now)
        assertEquals(3, planned.size)
        assertEquals(listOf("明天考试：考试课","今天考试：考试课","快考试了：考试课"), planned.map { it.title })
        assertEquals(1, ReminderPlanner.plan(null, listOf(exam), ReminderSettings(exams = true, eve = false, morning = false), now).size)
    }
    @Test fun locationIsRestrictedToTheCurrentHttpsCheckinOrigin() {
        val policy = cn.edu.xatu.helper.ui.CampusLinks
        assertTrue(policy.mayLocate("checkin","https://ehall.xatu.edu.cn/","https://ehall.xatu.edu.cn/xsfw/"))
        assertFalse(policy.mayLocate("leave","https://ehall.xatu.edu.cn/","https://ehall.xatu.edu.cn/"))
        assertFalse(policy.mayLocate("checkin","https://ehall.xatu.edu.cn.evil.example/","https://ehall.xatu.edu.cn/"))
        assertFalse(policy.mayLocate("checkin","https://ehall.xatu.edu.cn/","https://authserver.xatu.edu.cn/"))
        assertFalse(policy.mayLocate("checkin","https://ehall.xatu.edu.cn:8443/","https://ehall.xatu.edu.cn/"))
    }
    @Test fun historicDatesAndPastRemindersAreNotRescheduled() {
        val historic = table().copy(monday = "2025-09-01")
        assertTrue(ReminderPlanner.plan(historic, emptyList(), ReminderSettings(classes = true), now).isEmpty())
        val exam = Exam("e","考试课","c","","2026-11-01 09:00","教1","","")
        assertTrue(ReminderPlanner.plan(null, listOf(exam), ReminderSettings(exams = true), now).isEmpty())
    }
}
