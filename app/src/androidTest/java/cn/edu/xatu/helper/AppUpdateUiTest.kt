package cn.edu.xatu.helper

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.edu.xatu.helper.data.AppRelease
import cn.edu.xatu.helper.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun notificationRequiresConfirmationBeforeOpeningOfficialRelease() {
        var opened: String? = null
        compose.setContent {
            var offer by remember { mutableStateOf(AppRelease("v1.2.0", "改进与修复", "https://github.com/Kelejun/XATUHelper-Android/releases/tag/v1.2.0") as AppRelease?) }
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened = uri }
            }) {
                HelperTheme { UpdateNotification(UpdateUi(offer = offer), { offer = null }, {}) }
            }
        }
        compose.onNodeWithText("发现新版本 v1.2.0").assertIsDisplayed()
        compose.onNodeWithText("查看更新").performClick()
        compose.onNodeWithText("改进与修复").assertIsDisplayed()
        compose.runOnIdle { assertNull(opened) }
        compose.onNodeWithText("前往 GitHub 下载").performClick()
        compose.runOnIdle { assertEquals("https://github.com/Kelejun/XATUHelper-Android/releases/tag/v1.2.0", opened) }
        compose.onNodeWithText("更新至 v1.2.0").assertDoesNotExist()
    }

    @Test fun settingsShowsLastSuccessAndRunsManualCheck() {
        var checks = 0
        compose.setContent { HelperTheme {
            SettingsScreen(UiState(), {}, {}, {}, {}, update = UpdateUi(lastSuccess = 1_000_000), onCheckUpdates = { checks++ })
        } }
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("checkUpdates"))
        compose.onNodeWithTag("checkUpdates").performClick()
        compose.runOnIdle { assertEquals(1, checks) }
        compose.onNodeWithTag("updateLastSuccess").performScrollTo().assertTextContains("上次成功检查：", substring = true)
    }
}
