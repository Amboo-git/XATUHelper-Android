package cn.edu.xatu.helper.performance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Black-box driver in its own process: the app is fully R8-optimized and non-debuggable. */
@RunWith(AndroidJUnit4::class)
class TimetablePerformanceTest {
    @Test fun optimizedTimetableSwipesAndOfflineProcessRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val pkg = "cn.edu.xatu.helper.benchmark"
        val component = "$pkg/cn.edu.xatu.helper.MainActivity"
        val directory = instrumentation.context.getExternalFilesDir(null)!!
        fun click(text: String) {
            val node = device.wait(Until.findObject(By.text(text)), 15000)
            assertNotNull("Visible UI: $text", node)
            node.click()
            device.waitForIdle()
        }
        device.executeShellCommand("am start -W -n $component")
        click("体验校园助手 · 调试版")
        click("课程表")
        assertNotNull(device.wait(Until.findObject(By.text("高等数学")), 10000))
        click("高等数学")
        assertNotNull(device.wait(Until.findObject(By.text("课程号 DEMO0")), 10000))
        click("关闭详情")
        val w = device.displayWidth; val h = device.displayHeight
        // Warm up before resetting gfxinfo. This uses real input timing instead of Compose's test clock.
        repeat(2) {
            device.swipe(w*8/10, h/2, w*2/10, h/2, 30); Thread.sleep(350)
            device.swipe(w*2/10, h/2, w*8/10, h/2, 30); Thread.sleep(350)
        }
        device.executeShellCommand("dumpsys gfxinfo $pkg reset")
        repeat(8) {
            device.swipe(w*8/10, h/2, w*2/10, h/2, 30); Thread.sleep(350)
            device.swipe(w*2/10, h/2, w*8/10, h/2, 30); Thread.sleep(350)
        }
        assertNotNull(device.wait(Until.findObject(By.text("第 8 周")), 10000))
        val gfx = device.executeShellCommand("dumpsys gfxinfo $pkg framestats")
        File(directory, "optimized-gfxinfo.txt").writeText(gfx)
        assertTrue(gfx.contains("Total frames rendered"))
        assertTrue(device.takeScreenshot(File(directory, "timetable-benchmark.png")))
        // The benchmark fixture has no network dependency. Shut off both radios before killing the app.
        try {
            device.executeShellCommand("svc wifi disable"); device.executeShellCommand("svc data disable")
            device.executeShellCommand("am force-stop $pkg")
            val launch = device.executeShellCommand("am start -W -n $component")
            File(directory, "optimized-offline-launch.txt").writeText(launch)
            click("课程表")
            assertNotNull(device.wait(Until.findObject(By.text("高等数学")), 10000))
            assertTrue(device.takeScreenshot(File(directory, "optimized-offline-restart.png")))
        } finally {
            device.executeShellCommand("svc wifi enable"); device.executeShellCommand("svc data enable")
        }
    }
}
