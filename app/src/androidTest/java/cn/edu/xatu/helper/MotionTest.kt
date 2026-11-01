package cn.edu.xatu.helper

import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.edu.xatu.helper.ui.Reveal
import cn.edu.xatu.helper.ui.ScreenTransition
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class MotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun enteringScreenActuallyRendersIntermediateFrames() {
        assumeTrue(Settings.Global.getFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f)
        var page by mutableStateOf(0)
        compose.runOnUiThread { compose.activity.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                ScreenTransition(page, Modifier.testTag("motion")) { Box(Modifier.fillMaxSize().background(Color.Blue)) }
            }
        } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { page = 1 }
        compose.mainClock.advanceTimeBy(96)
        fun centerRed(): Float {
            val image = compose.onNodeWithTag("motion").captureToImage()
            return image.toPixelMap()[image.width / 2, image.height / 2].red
        }
        val intermediate = centerRed()
        compose.mainClock.advanceTimeBy(400)
        val settled = centerRed()
        assertTrue("Expected a partially faded screen, got $intermediate", intermediate > settled + .01f && intermediate < .99f)
        assertTrue("Expected the final blue screen, got red=$settled", settled < .01f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun pageReplacementDisposesOldContentAndRevealCanCollapse() {
        var page by mutableStateOf(0)
        var expanded by mutableStateOf(false)
        val active = mutableSetOf<Int>()
        compose.runOnUiThread { compose.activity.setContent {
            ScreenTransition(page) { current ->
                DisposableEffect(current) { active.add(current); onDispose { active.remove(current) } }
                Column {
                    Box(Modifier.size(40.dp).testTag("page$current"))
                    Reveal(expanded) { Box(Modifier.size(80.dp).testTag("details")) }
                }
            }
        } }
        compose.waitForIdle()
        compose.runOnIdle { page = 1; expanded = true }
        compose.onNodeWithTag("page0").assertDoesNotExist()
        compose.onNodeWithTag("page1").assertIsDisplayed()
        compose.onNodeWithTag("details").assertIsDisplayed()
        compose.runOnIdle { assertEquals(setOf(1), active); expanded = false }
        compose.onNodeWithTag("details").assertDoesNotExist()
        compose.runOnIdle { page = 2 }
        compose.onNodeWithTag("page2").assertIsDisplayed()
        compose.runOnIdle { assertEquals(setOf(2), active) }
    }
}
