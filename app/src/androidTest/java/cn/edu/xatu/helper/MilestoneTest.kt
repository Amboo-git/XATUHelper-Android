package cn.edu.xatu.helper

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import cn.edu.xatu.helper.model.schoolToday
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class MilestoneTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun startAtQaLogin() {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa"))
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                compose.activity.setShowWhenLocked(true)
                compose.activity.setTurnScreenOn(true)
            }
        }
        lateinit var model: cn.edu.xatu.helper.ui.HelperViewModel
        compose.runOnUiThread { model = androidx.lifecycle.ViewModelProvider(compose.activity)[cn.edu.xatu.helper.ui.HelperViewModel::class.java] }
        compose.waitUntil(10000) { !model.state.value.starting && !model.state.value.busy }
        if (model.state.value.account.isNotBlank()) {
            compose.runOnIdle { model.logout() }
            compose.waitUntil(10000) { model.state.value.account.isBlank() && !model.state.value.busy }
        }
    }

    @Test fun loginValidationDemoNavigationTimetableAndRecreation() {
        compose.runOnUiThread { compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("周次校准（可选）").assertDoesNotExist()
        compose.onNodeWithText("第一周周一 · yyyy-MM-dd").assertDoesNotExist()
        screenshot("login-light.png")
        compose.onNodeWithTag("login").performScrollTo().performClick()
        compose.onNodeWithText("请填写学号和密码").assertIsDisplayed()
        compose.onNodeWithTag("demo").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("tab1").fetchSemanticsNodes().isNotEmpty() }
        screenshot("home-light.png")
        compose.onNodeWithTag("tab1").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("week").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("week").assertTextEquals("第 8 周")
        compose.onNodeWithTag("periodStart3").assertTextEquals("10:10/\n10:20").assertContentDescriptionEquals("10:10/10:20")
        compose.onNodeWithContentDescription("校准周次").performClick()
        compose.onNodeWithTag("actualWeek").assertIsDisplayed()
        compose.onNodeWithText("按这个周次校正").performClick()
        compose.onNodeWithTag("week").assertTextEquals("第 8 周")
        compose.onNodeWithContentDescription("选择周次").performClick()
        compose.onNodeWithText("第 1 周").performScrollTo().performClick()
        compose.onNodeWithTag("week").assertTextEquals("第 1 周")
        compose.onNodeWithText("本周").performClick()
        compose.onNodeWithTag("week").assertTextEquals("第 8 周")
        compose.onNodeWithContentDescription("隐藏周末").performClick()
        compose.onNodeWithTag("nextWeek").performClick()
        compose.onNodeWithTag("week").assertTextEquals("第 9 周")
        compose.onNodeWithTag("previousWeek").performClick()
        compose.onNodeWithText("高等数学").performClick()
        compose.onNodeWithText("课程号 DEMO0").assertIsDisplayed()
        compose.onNodeWithText("关闭详情").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("weekPager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("week").assertTextEquals("第 9 周")
        compose.onNodeWithTag("weekPager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("week").assertTextEquals("第 8 周")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("tab1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab1").performClick()
        compose.onNodeWithTag("week").assertTextEquals("第 8 周")
        compose.onNodeWithText("高等数学").assertIsDisplayed()
        compose.onNodeWithContentDescription("显示周末").assertIsDisplayed().performClick()
        screenshot("timetable-light.png")
        // A reproducible warm interaction sample. Emulator timings are diagnostic, not a real-device FPS claim.
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        val thread = HandlerThread("frame-sample").apply { start() }
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ -> frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)) }
        compose.runOnUiThread { compose.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        try {
            repeat(8) {
                compose.onNodeWithTag("weekPager").performTouchInput { swipeLeft(durationMillis = 300) }
                compose.waitForIdle()
                compose.onNodeWithTag("weekPager").performTouchInput { swipeRight(durationMillis = 300) }
                compose.waitForIdle()
            }
        } finally {
            compose.runOnUiThread { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) }; thread.quitSafely()
        }
        val sorted = synchronized(frames) { frames.toList().sorted() }
        assertTrue("Window frame samples should be captured", sorted.size > 10)
        val stats = "${BuildConfig.BUILD_TYPE}-device frames=${sorted.size} p50ms=${sorted[(sorted.size-1)/2]/1e6} p95ms=${sorted[((sorted.size-1)*.95).toInt()]/1e6} over16ms=${sorted.count { it > 16_666_667 }}"
        println("XATU_NATIVE_FRAMES $stats")
        File(compose.activity.getExternalFilesDir(null), "frame-sample.txt").writeText(stats)
        compose.onNodeWithTag("tab2").performClick()
        compose.onNodeWithTag("logout").assertDoesNotExist()
        compose.onNodeWithText("考试安排").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("examsList").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("高等数学").assertIsDisplayed()
        compose.onNodeWithTag("examCountdown:e1").assertIsDisplayed().assertTextEquals("还有 14 天")
        screenshot("exams.png")
        compose.onNodeWithContentDescription("返回校园").performClick()
        compose.onNodeWithText("成绩").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("gradeSearch").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("gradeSearch").performScrollTo().performTextInput("  程序  ")
        compose.onNodeWithText("程序设计").assertIsDisplayed()
        screenshot("grades.png")
        compose.onNodeWithContentDescription("返回校园").performClick()
        compose.onNodeWithText("学业完成情况").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("planSearch").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("planSearch").performScrollTo().performTextInput("  物理  ")
        compose.onNodeWithText("大学物理").assertIsDisplayed()
        screenshot("plan.png")
        compose.onNodeWithContentDescription("返回校园").performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("示例账号").assertIsDisplayed()
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("diagnosticsSetting"))
        compose.onNodeWithTag("diagnosticsSetting").performClick()
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("diagnostics"))
        compose.onNodeWithTag("diagnostics").assertIsDisplayed()
        compose.onNodeWithTag("settings").performScrollToNode(hasText("复制诊断数据"))
        compose.onNodeWithText("复制诊断数据").assertIsDisplayed()
        screenshot("diagnostics.png")
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("appVersion"))
        compose.onNodeWithTag("appVersion").assertTextEquals("XATune ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT})")
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("githubRepository"))
        compose.onNodeWithTag("githubRepository").assertHasClickAction().assertIsDisplayed()
        compose.onNodeWithText("Kelejun/XATUHelper-Android").assertIsDisplayed()
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("logout"))
        compose.onNodeWithTag("logout").assertIsDisplayed()
        screenshot("settings.png")
        compose.onNodeWithContentDescription("返回校园").performClick()
        compose.onNodeWithTag("campus").performScrollToNode(hasText("电费"))
        compose.onNodeWithText("电费").performClick()
        compose.onNodeWithText("选择宿舍").performClick()
        fun waitFor(text: String) { compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        waitFor("电费项目：请选择")
        compose.onNodeWithText("电费项目：请选择").performScrollTo().performClick()
        compose.onNodeWithText("示例电费").performClick()
        waitFor("校区：请选择")
        compose.onNodeWithText("校区：请选择").performScrollTo().performClick()
        compose.onNodeWithText("示例校区").performClick()
        waitFor("楼栋：请选择")
        compose.onNodeWithText("楼栋：请选择").performScrollTo().performClick()
        compose.onNodeWithText("9号楼").performClick()
        waitFor("房间：请选择")
        compose.onNodeWithTag("roomSearch").performScrollTo().performClick().performTextInput("328")
        fun imeVisible() = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom?.let {
                it > 100 * compose.activity.resources.displayMetrics.density
            } == true
        compose.waitUntil(5000) { imeVisible() }
        compose.onNodeWithTag("roomSearch").assertIsDisplayed()
        compose.onNodeWithTag("electricity").performScrollToNode(hasText("保存并查询"))
        compose.onNodeWithText("保存并查询").assertIsDisplayed()
        screenshot("electricity-keyboard.png")
        compose.onNodeWithTag("electricity").performScrollToNode(hasText("房间：请选择"))
        compose.onNodeWithText("房间：请选择").performClick()
        compose.onNode(hasText("328") and hasAnyAncestor(isPopup())).performClick()
        compose.waitUntil(5000) { !imeVisible() }
        compose.onNodeWithText("保存并查询").performScrollTo().performClick()
        waitFor("68.50 度")
        compose.onNodeWithTag("electricBalance").assertTextEquals("68.50 度")
        compose.onNodeWithText("更换宿舍").assertDoesNotExist()
        compose.onNodeWithText("选择宿舍").assertDoesNotExist()
        compose.onNodeWithText("近七天用电").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("余额趋势").performClick()
        compose.onNodeWithText("近七天余额（度）").performScrollTo().assertIsDisplayed()
        screenshot("electricity.png")
        compose.onNodeWithContentDescription("返回校园").performClick()
        compose.onNodeWithTag("campus").performScrollToNode(hasText("校历"))
        compose.onNodeWithText("校历").performClick()
        waitFor("示例校历")
        compose.onNodeWithText("示例校历").assertIsDisplayed()
        val summerEnd = "${schoolToday().year + 1}-07-11 — ${schoolToday().year + 1}-08-29"
        compose.onNodeWithText(summerEnd).performScrollTo().assertIsDisplayed()
        screenshot("calendar.png")
        compose.onNodeWithContentDescription("返回校园").performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithTag("settings").performScrollToNode(hasText("提醒设置"))
        compose.onNodeWithText("提醒设置").performClick()
        compose.onNodeWithText("上课提醒").assertIsDisplayed()
        screenshot("reminders.png")
        compose.onNodeWithContentDescription("返回校园").performClick()
        compose.onNodeWithTag("settings").performScrollToNode(hasText("桌面小组件"))
        compose.onNodeWithText("桌面小组件").performClick()
        compose.onNodeWithText("添加课程").assertIsDisplayed()
        screenshot("widget-settings.png")
        // Copy last so the Android clipboard overlay does not obscure later screenshots.
        compose.onNodeWithContentDescription("返回校园").performClick()
        compose.onNodeWithTag("settings").performScrollToNode(hasText("复制诊断数据"))
        compose.onNodeWithText("复制诊断数据").performClick()
        compose.onNodeWithText("已复制诊断数据").assertIsDisplayed()
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val input = compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            input.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
