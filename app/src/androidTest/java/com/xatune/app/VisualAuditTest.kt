package com.xatune.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xatune.app.ui.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Device-only visual review in the QA package; theme/font overrides never change system settings. */
class VisualAuditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun reviewPagesInLightDarkAndLargeText() {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa"))
        lateinit var model: HelperViewModel
        var profile by mutableIntStateOf(0)
        var login by mutableStateOf(true)
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                compose.activity.setShowWhenLocked(true)
                compose.activity.setTurnScreenOn(true)
            }
            model = ViewModelProvider(compose.activity)[HelperViewModel::class.java]
            compose.activity.setContent {
                val base = LocalDensity.current
                val configuration = Configuration(LocalConfiguration.current).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (profile == 1) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                CompositionLocalProvider(LocalConfiguration provides configuration,
                    LocalDensity provides Density(base.density, if (profile == 2) 1.3f else 1f)) {
                    HelperTheme { key(profile, login) {
                        if (login) LoginScreen(UiState(starting = false), { _, _ -> }, {}, null, null)
                        else HelperApp(model)
                    } }
                }
            }
        }
        compose.waitUntil(10000) { !model.state.value.starting && !model.state.value.busy }
        compose.runOnIdle { model.demo() }
        compose.waitUntil(10000) { model.state.value.demo && !model.state.value.busy }
        for ((index, name) in listOf("light", "dark", "large").withIndex()) {
            compose.runOnIdle { login = true; profile = index }
            shot("$name-login")
            compose.runOnIdle { login = false }
            compose.onNodeWithTag("home").assertExists()
            shot("$name-home")
            compose.onNodeWithTag("tab1").performClick()
            compose.onNodeWithTag("week").assertIsDisplayed()
            shot("$name-timetable")
            compose.onNodeWithContentDescription("校准周次").performClick()
            compose.onNodeWithTag("actualWeek").assertIsDisplayed()
            shot("$name-calibration")
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithText("高等数学").performClick()
            compose.onNodeWithText("关闭详情").assertIsDisplayed()
            shot("$name-course-detail")
            compose.onNodeWithText("关闭详情").performClick()
            compose.onNodeWithTag("tab2").performClick()
            shot("$name-campus")
            for ((title, tag, file) in listOf(Triple("考试安排", "examsList", "exams"), Triple("成绩", "gradesList", "grades"), Triple("学业完成情况", "planList", "plan"))) {
                compose.onNodeWithTag("campus").performScrollToNode(hasText(title))
                compose.onNodeWithText(title).performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
                if (file == "grades") {
                    val pixels = compose.onNodeWithText("平均绩点", substring = true).captureToImage().toPixelMap()
                    val background = pixels[0, 0].toArgb()
                    var contrast = 1.0
                    for (x in 0 until pixels.width step 2) for (y in 0 until pixels.height step 2) {
                        contrast = maxOf(contrast, androidx.core.graphics.ColorUtils.calculateContrast(pixels[x, y].toArgb(), background))
                    }
                    assertTrue("Summary text must remain readable in $name mode", contrast >= 4.5)
                }
                shot("$name-$file")
                compose.onNodeWithContentDescription("返回校园").performClick()
            }
            for ((title, tag, file) in listOf(Triple("电费", "electricity", "electricity"), Triple("校历", "schoolCalendar", "calendar"))) {
                compose.onNodeWithTag("campus").performScrollToNode(hasText(title))
                compose.onNodeWithText(title).performClick()
                compose.onNodeWithTag(tag).assertExists()
                if (file == "calendar") compose.waitUntil(10000) { compose.onAllNodesWithText("示例校历").fetchSemanticsNodes().isNotEmpty() }
                shot("$name-$file")
                compose.onNodeWithContentDescription("返回校园").performClick()
            }
            compose.onNodeWithContentDescription("设置").performClick()
            shot("$name-settings")
            compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("appVersion"))
            compose.onNodeWithTag("appVersion").assertTextEquals("XATune ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT})")
            shot("$name-about")
            compose.onNodeWithTag("settings").performScrollToNode(hasText("提醒设置"))
            compose.onNodeWithText("提醒设置").performClick()
            compose.onNodeWithTag("reminders").assertExists()
            shot("$name-reminders")
            compose.onNodeWithContentDescription("返回校园").performClick()
            compose.onNodeWithTag("settings").performScrollToNode(hasText("桌面小组件"))
            compose.onNodeWithText("桌面小组件").performClick()
            compose.onNodeWithText("添加课程").performScrollTo().assertIsDisplayed()
            shot("$name-widgets")
            compose.onNodeWithText("添加课程").performClick()
            compose.onNodeWithText("申请添加桌面小组件").assertIsDisplayed()
            shot("$name-widget-permission")
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText("申请添加桌面小组件").assertDoesNotExist()
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(150)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(compose.activity.getExternalFilesDir(null), "md3-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
