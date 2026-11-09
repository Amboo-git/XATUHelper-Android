package com.xatune.app

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xatune.app.data.*
import com.xatune.app.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test fun loginToDurableCacheRefreshRecoveryAndLogout() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var manager: CookieManager
        instrumentation.runOnMainSync { manager = CookieManager.getInstance() }
        val vault = SecretVault(context); val cookies = SchoolCookies(manager, vault); val store = LocalStore(context)
        MockWebServer().use { server ->
            server.start()
            val base = server.url("/eams/").toString(); val login = server.url("/login").toString()
            val network = SchoolNetwork(cookies, base, login)
            val repository = TimetableRepository(store, vault, cookies, network)
            val shell = """<script>semesterCalendar({value:"101"}); add("ids","111"); add("ids","222");</script>"""
            val semesters = """{semesters:{y:[{id:101,schoolYear:"2026-2027",name:"1",startDate:"2026-09-07"}]}}"""
            val table = """var table0 = new CourseTable(2026,84); var unitCount = 12;
                var actTeachers = [{name:"示例教师"}];
                new TaskActivity(a,b,"1(001)","集成测试课程(001)","1","教1-101","0111",null);
                index=0*unitCount+0; index=0*unitCount+1;
            """
            fun response(body: String) { server.enqueue(MockResponse().setBody(body)) }
            try {
                repository.logout()
                response("""<form><input name='password'><input id='pwdEncryptSalt' value='0123456789abcdef'><input id='execution' value='e1s1'></form>""")
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", base + "homeExt.action"))
                response("教务首页"); response(shell); response("1"); response(semesters); response(table)
                val loaded = repository.login("integration-fixture", "fixture-password")
                assertEquals(2, loaded.courses.size); assertEquals("2026-09-07", loaded.monday)
                assertEquals("fixture-password", vault.load().credentials?.password)
                val restarted = TimetableRepository(store, vault, cookies, network)
                restarted.initialize()
                assertEquals(loaded, store.table("integration-fixture", "101", TableKind.STUDENT))
                assertNull(store.table("another-account", "101", TableKind.STUDENT))
                // Academic payloads share the verified session but remain isolated by scope.
                response("""semesterCalendar({value:"999"})""")
                val gradeHtml = """<table><tr><th>课程名称</th><th>学分</th><th>总评成绩</th></tr><tr><td>示例课程</td><td>3</td><td>88</td></tr></table>"""
                response(gradeHtml)
                val academic = repository.refreshAcademic(loaded.account, AcademicKind.GRADES, "101")
                assertEquals("88", academic.grades!!.grades.single().score)
                assertEquals(academic, store.academic(loaded.account, AcademicKind.GRADES, "101"))
                assertNull(store.academic(loaded.account, AcademicKind.GRADES, "all"))
                server.enqueue(MockResponse().setResponseCode(503))
                try { repository.refreshAcademic(loaded.account, AcademicKind.GRADES, "all"); fail("Expected HTTP failure") } catch (_: java.io.IOException) { }
                assertEquals(academic, store.academic(loaded.account, AcademicKind.GRADES, "101"))
                // A manual plan refresh re-reads its catalog, and a failed catalog keeps usable filters.
                val planHtml = "<table><tr class='darkColumn'><td>基础课程</td><td>160</td><td>96</td></tr></table>"
                val oldCatalog = listOf(CourseInfo("MATH01", "高等数学", "必修", "5", "80", "1"))
                store.saveAcademic(loaded.account, AcademicKind.PLAN, "all", AcademicSnapshot(catalog = oldCatalog))
                val beforePlan = server.requestCount
                response(planHtml)
                assertEquals(oldCatalog, repository.refreshAcademic(loaded.account, AcademicKind.PLAN, "all").catalog)
                assertEquals(beforePlan + 1, server.requestCount)
                response(planHtml)
                server.enqueue(MockResponse().setResponseCode(503))
                val retained = repository.refreshAcademic(loaded.account, AcademicKind.PLAN, "all", forceCatalog = true)
                assertEquals(beforePlan + 3, server.requestCount)
                assertEquals(oldCatalog, retained.catalog)
                assertNotNull(retained.catalogWarning)
                val cells = "<td>MATH01</td><td class='course'>1 高等数学</td>" + listOf("5", "80", "", "", "", "", "", "3", "", "是").joinToString("") { "<td>$it</td>" }
                response(planHtml); response("<table><tr>$cells</tr></table>")
                assertEquals("3", repository.refreshAcademic(loaded.account, AcademicKind.PLAN, "all", forceCatalog = true).catalog.single().term)
                // HTTP failure must preserve the last successful snapshot.
                response(semesters); server.enqueue(MockResponse().setResponseCode(503))
                try { repository.refresh(loaded.account, "101", TableKind.STUDENT); fail("Expected HTTP failure") } catch (_: java.io.IOException) { }
                assertEquals(loaded, store.table(loaded.account, "101", TableKind.STUDENT))
                // An expired request is renewed once, then the original operation is retried.
                response("<input name='password'>")
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", base + "homeExt.action"))
                response("教务首页"); response(shell); response("1"); response(semesters); response(table)
                assertEquals(2, repository.refresh(loaded.account, "101", TableKind.STUDENT).courses.size)
                val requests = server.requestCount
                response("<input name='password'>")
                try { repository.refresh(loaded.account, "101", TableKind.STUDENT); fail("Expected cooldown") } catch (_: SessionExpired) { }
                assertEquals(requests + 1, server.requestCount)
                repository.logout()
                assertNull(store.table(loaded.account, "101", TableKind.STUDENT))
                assertTrue(store.account().isEmpty()); assertNull(vault.load().credentials)
            } finally { repository.logout() }
        }
    }
}
