package com.xatune.app

import android.content.res.Configuration
import androidx.activity.BackEventCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.xatune.app.ui.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun keepTestActivityVisible() {
        compose.runOnUiThread {
            // Scope wake/keep-awake to this QA activity; do not change device settings.
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                compose.activity.setTurnScreenOn(true)
                compose.activity.setShowWhenLocked(true)
            }
        }
    }


    @Test fun predictiveGestureCancelsRestoresAndOnlyCommitsOnce() {
        var navigations = 0
        var enabled by mutableStateOf(true)
        compose.runOnUiThread { compose.activity.setContent {
            Box(Modifier.fillMaxSize().background(Color.White).testTag("root")) {
                Box(Modifier.fillMaxSize().then(Modifier.predictiveBack(enabled) { navigations++ }).background(Color.Blue))
            }
        } }
        compose.waitForIdle()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(80f, 200f, .7f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        fun edgeRed(): Float {
            val pixels = compose.onNodeWithTag("root").captureToImage().toPixelMap()
            return pixels[2, pixels.height / 2].red
        }
        assertTrue("Gesture should visually transform the page", edgeRed() > .9f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(0, navigations)
        assertTrue("Cancellation restores the full page", edgeRed() < .1f)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_RIGHT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(80f, 200f, .8f, BackEventCompat.EDGE_RIGHT))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertEquals(1, navigations)
        compose.runOnIdle { enabled = false }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse("Root must leave back-to-home to Android", dispatcher.hasEnabledCallbacks()) }
    }

    @Test fun widgetAdditionExplainsPermissionsBeforeRequestingLauncher() {
        compose.runOnUiThread { compose.activity.setContent {
            HelperTheme { WidgetSettings({}, CampusState()) }
        } }
        compose.onNodeWithText("桌面添加权限设置").assertIsDisplayed()
        compose.onNodeWithText("添加课程").performClick()
        compose.onNodeWithText("申请添加桌面小组件").assertIsDisplayed()
        compose.onNodeWithText("先开启权限").assertIsDisplayed()
        compose.onNodeWithText("申请添加").assertIsDisplayed()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("申请添加桌面小组件").assertDoesNotExist()
        compose.onNodeWithText("添加课程").assertIsDisplayed()
    }

    @Test fun widgetPaletteResolvesForLightAndDarkConfigurations() {
        val context = compose.activity
        for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
            val themed = context.createConfigurationContext(config)
            val background = themed.getColor(R.color.widget_surface)
            val foreground = themed.getColor(R.color.widget_text)
            assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(foreground, background) >= 4.5)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val expected = if (night == Configuration.UI_MODE_NIGHT_YES) android.R.color.system_accent1_800 else android.R.color.system_accent1_100
                assertEquals(themed.getColor(expected), background)
            }
        }
    }
}
