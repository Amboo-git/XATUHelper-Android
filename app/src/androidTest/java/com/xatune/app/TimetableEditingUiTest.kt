package com.xatune.app

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.setContent
import com.xatune.app.ui.HelperApp
import com.xatune.app.ui.HelperTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xatune.app.model.*
import com.xatune.app.ui.HelperViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TimetableEditingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: HelperViewModel
    @Before fun prepare() {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa"))
        compose.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (android.os.Build.VERSION.SDK_INT >= 27) { compose.activity.setShowWhenLocked(true); compose.activity.setTurnScreenOn(true) }
            model = ViewModelProvider(compose.activity)[HelperViewModel::class.java]
        }
        compose.waitUntil(10000) { !model.state.value.starting && !model.state.value.busy }
        val app = compose.activity.application as HelperApplication
        runBlocking { app.store.edits("__demo__", "demo", emptyList()) }
        compose.runOnIdle { model.demo() }
        ready()
        compose.onNodeWithTag("tab1").performClick()
    }
    private fun ready() { compose.waitUntil(10000) { !model.state.value.busy && model.state.value.table != null } }
    @After fun cleanup() = runBlocking { (compose.activity.application as HelperApplication).store.edits("__demo__", "demo", emptyList()) }

    @Test fun addMoveRemoveUndoAndRecreate() {
        compose.onNodeWithContentDescription("添加课程").performClick()
        compose.onNodeWithTag("courseName").performTextInput("自主学习")
        compose.onNodeWithTag("courseRoom").performTextInput("图书馆")
        compose.onNode(hasText("周三") and hasAnyAncestor(hasTestTag("courseEditor"))).performScrollTo().performClick()
        shot("course-editor.png")
        compose.onNodeWithTag("saveCourseEdit").performClick()
        ready()
        compose.waitUntil(5000) { model.state.value.edits.size == 1 }
        compose.onNodeWithText("自主学习").performClick()
        compose.onNodeWithText("修改课程").performClick()
        compose.onNodeWithTag("courseRoom").performTextReplacement("自习室")
        compose.onNodeWithTag("saveCourseEdit").performClick()
        compose.waitUntil(5000) { model.state.value.edits.size == 2 && !model.state.value.busy }
        compose.onNodeWithText("自主学习").performClick()
        compose.onAllNodesWithText("自习室").onLast().assertIsDisplayed()
        compose.onNodeWithText("移除课程").performClick()
        compose.onNodeWithTag("saveCourseEdit").performClick()
        compose.waitUntil(5000) { model.state.value.edits.size == 3 && !model.state.value.busy }
        compose.onNodeWithText("自主学习").assertDoesNotExist()
        compose.onNodeWithContentDescription("我的课表修改").performClick()
        shot("course-history.png")
        compose.onNodeWithText("撤销上一次修改").performClick()
        compose.onNodeWithText("撤销修改").performClick()
        compose.waitUntil(5000) { model.state.value.edits.size == 2 && !model.state.value.busy }
        compose.onNodeWithContentDescription("关闭修改记录").performClick()
        compose.activityRule.scenario.recreate()
        reopenFromStorage()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("tab1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab1").performClick()
        compose.onNodeWithText("自主学习").assertIsDisplayed()
        shot("edited-timetable.png")
    }
    @Test fun conflictRequiresExplicitConfirmation() {
        compose.onNodeWithContentDescription("添加课程").performClick()
        compose.onNodeWithTag("courseName").performTextInput("冲突课程")
        compose.onNodeWithTag("saveCourseEdit").performClick()
        compose.onNodeWithText("时间冲突").assertIsDisplayed()
        compose.onNodeWithText("返回调整").performClick()
        assertTrue(model.state.value.edits.isEmpty())
        compose.onNodeWithTag("saveCourseEdit").performClick()
        shot("course-conflict.png")
        compose.onNodeWithText("仍然保存").performClick()
        compose.waitUntil(5000) { model.state.value.edits.size == 1 && !model.state.value.busy }
        compose.onNodeWithText("高等数学").assertIsDisplayed()
        compose.onNodeWithText("冲突课程").assertIsDisplayed()
    }
    @Test fun changedOfficialCourseCanBeReappliedFromHistory() {
        val app = compose.activity.application as HelperApplication
        runBlocking {
            val base = requireNotNull(app.store.baseTable("__demo__", "demo", TableKind.STUDENT))
            val original = blocksFor(base.courses, 8).first { it.key == "DEMO0" }
            val move = TimetableEdit(kind = EditKind.MOVE, source = sourceFor(base, original, listOf(8)), destination = EditDestination(6, 1, "调整后教室"))
            app.store.edits(base.account, "demo", listOf(move))
            app.store.save(base.copy(courses = base.courses.map { if (it.key == "DEMO0") it.copy(room = "教务新教室") else it }))
        }
        reopenFromStorage()
        compose.onNodeWithTag("tab1").performClick()
        compose.onNodeWithContentDescription("我的课表修改").performClick()
        compose.onNodeWithText("待确认 · 第 8 周").assertIsDisplayed()
        shot("course-pending.png")
        compose.onNode(hasText("重新应用：", substring = true)).performScrollTo().performClick()
        compose.onNodeWithText("重新应用").performClick()
        compose.waitUntil(5000) { !model.state.value.busy && model.state.value.pendingEdits.isEmpty() }
        compose.onNodeWithContentDescription("关闭修改记录").performClick()
        assertEquals("调整后教室", blocksFor(model.state.value.table!!.courses,8).first { it.key == "DEMO0" }.room)
        assertEquals(6, blocksFor(model.state.value.table!!.courses,8).first { it.key == "DEMO0" }.day)
    }
    private fun reopenFromStorage() {
        compose.runOnUiThread {
            val app = compose.activity.application as HelperApplication
            model = HelperViewModel(app.repository, app.campusRepository)
            compose.activity.setContent { HelperTheme { HelperApp(model) } }
        }
        compose.waitUntil(10000) { !model.state.value.starting && !model.state.value.busy }
    }
    @Test fun storageKeepsOfficialSnapshotAndIsolatesAccountsAndSemesters() = runBlocking {
        val app = compose.activity.application as HelperApplication
        val base = requireNotNull(app.store.baseTable("__demo__", "demo", TableKind.STUDENT))
        val original = blocksFor(base.courses, 8).first { it.key == "DEMO0" }
        val move = TimetableEdit(kind = EditKind.MOVE, source = sourceFor(base, original, listOf(8)), destination = EditDestination(6, 1, "新教室"))
        app.store.edits(base.account, "demo", listOf(move))
        assertEquals(base, app.store.baseTable(base.account, "demo", TableKind.STUDENT))
        assertEquals("新教室", blocksFor(app.store.table(base.account, "demo", TableKind.STUDENT)!!.courses,8).first { it.key == original.key }.room)
        app.store.save(base.copy(fetchedAt = base.fetchedAt + 1))
        assertEquals(6, blocksFor(app.store.table(base.account, "demo", TableKind.STUDENT)!!.courses,8).first { it.key == original.key }.day)
        val other = base.copy(account = "another", semester = Semester("other", "其他学期"))
        app.store.save(other)
        assertEquals(other, app.store.table("another", "other", TableKind.STUDENT))
        val calibrated = app.repository.calibrate(app.store.table(base.account, "demo", TableKind.STUDENT)!!, java.time.LocalDate.parse(base.monday).plusWeeks(1))
        assertEquals(base.courses, calibrated.courses)
        app.store.clear()
        assertEquals(listOf(move), app.store.edits(base.account, "demo"))
        app.store.save(base); app.store.select(base.account, "demo", TableKind.STUDENT)
        assertEquals(6, blocksFor(app.store.table(base.account, "demo", TableKind.STUDENT)!!.courses,8).first { it.key == original.key }.day)
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(400)
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
}
