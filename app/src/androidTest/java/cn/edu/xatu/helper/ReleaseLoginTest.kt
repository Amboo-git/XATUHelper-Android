package cn.edu.xatu.helper

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.edu.xatu.helper.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReleaseLoginTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun productionLoginOffersCredentialsAndWebWithoutSampleOrFirstWeek() {
        compose.runOnUiThread {
            compose.activity.setContent {
                HelperTheme { LoginScreen(UiState(starting = false), { _, _ -> }, {}, null, null) }
            }
        }
        compose.onNodeWithTag("account").assertIsDisplayed()
        compose.onNodeWithTag("password").assertIsDisplayed()
        compose.onNodeWithTag("login").assertIsDisplayed()
        compose.onNodeWithText("使用网页登录").assertIsDisplayed()
        compose.onNodeWithTag("demo").assertDoesNotExist()
        compose.onNodeWithText("周次校准（可选）").assertDoesNotExist()
        compose.onNodeWithText("第一周周一 · yyyy-MM-dd").assertDoesNotExist()
        compose.waitForIdle()
        // The previous navigation test copies diagnostics; allow the OS clipboard preview to close.
        Thread.sleep(6500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "login-production.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
