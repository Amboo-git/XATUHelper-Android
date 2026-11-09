package com.xatune.app

import android.view.View
import android.view.MotionEvent
import android.view.InputDevice
import android.os.SystemClock
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.xatune.app.ui.CampusLinks
import com.xatune.app.ui.CampusWeb
import com.xatune.app.ui.WebLogin
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CampusWebRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Before fun prepare() {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa"))
        compose.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.setShowWhenLocked(true); compose.activity.setTurnScreenOn(true)
        }
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
    }
    private fun findWeb(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWeb(view.getChildAt(it)) }
        else -> null
    }
    private fun browser(): WebView {
        var web: WebView? = null
        compose.waitUntil(10_000) { compose.runOnUiThread { web = findWeb(compose.activity.window.decorView) }; web != null }
        return web!!
    }
    private fun snapshot(web: WebView): JSONObject {
        val latch = CountDownLatch(1)
        var result = JSONObject()
        compose.runOnUiThread {
            web.evaluateJavascript("JSON.stringify({vh:document.getElementById('viewport-probe')?.getBoundingClientRect().height||0,inner:innerHeight,clientWidth:document.documentElement.clientWidth,frames:Array.from(document.querySelectorAll('iframe')).map(f=>{try{return {path:f.contentWindow.location.pathname,styles:f.contentDocument.styleSheets.length,rules:Array.from(f.contentDocument.styleSheets).reduce((n,s)=>n+s.cssRules.length,0)}}catch(e){return {error:e.name}}}),styles:document.styleSheets.length,jquery:typeof jQuery,resize:typeof resizeIframe,ready:document.readyState,old:!!window.__xatuRegressionOldDocument,path:location.pathname})") {
                val decoded = org.json.JSONTokener(it).nextValue() as? String
                if (decoded != null) result = JSONObject(decoded)
                latch.countDown()
            }
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return result
    }
    private fun awaitSnapshot(web: WebView, predicate: (JSONObject) -> Boolean): JSONObject {
        val until = System.currentTimeMillis() + 45_000
        var result = JSONObject()
        while (System.currentTimeMillis() < until) {
            result = snapshot(web)
            if (result.optString("ready") == "complete" && predicate(result)) { Thread.sleep(400); val settled = snapshot(web); if (settled.optString("ready") == "complete" && predicate(settled)) return settled }
            Thread.sleep(250)
        }
        fail("WebView state did not converge: $result")
        return result
    }
    private fun viewportFixture(web: WebView) {
        compose.runOnUiThread {
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, web.layoutParams.width)
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, web.layoutParams.height)
            web.stopLoading()
            web.loadDataWithBaseURL("https://ehall.xatu.edu.cn/", "<meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0'><div id='viewport-probe' style='height:100vh'></div>", "text/html", "UTF-8", null)
        }
    }
    @Test fun campusViewportTracksAvailableHeight() {
        val original = CampusLinks.pages.getValue("leave")
        CampusLinks.pages["leave"] = "请销假" to "about:blank"
        try {
            val height = mutableStateOf(600.dp)
            compose.runOnUiThread { compose.activity.setContent { Box(Modifier.height(height.value)) { CampusWeb("leave", {}) } } }
            val web = browser()
            viewportFixture(web)
            val full = awaitSnapshot(web) { it.optDouble("vh") > 300 }
            assertEquals(full.getDouble("inner"), full.getDouble("vh"), 2.0)
            compose.runOnUiThread { height.value = 380.dp }
            compose.waitForIdle()
            assertSame(web, browser())
            val smaller = awaitSnapshot(web) { it.optDouble("vh") > 0 && it.optDouble("vh") < full.getDouble("vh") - 100 }
            assertEquals(smaller.getDouble("inner"), smaller.getDouble("vh"), 2.0)
        } finally { CampusLinks.pages["leave"] = original }
    }
    @Test fun loginViewportHasNonzeroCssHeight() {
        compose.runOnUiThread { compose.activity.setContent { WebLogin("fixture", {}, {}, {}) } }
        val web = browser()
        viewportFixture(web)
        val state = awaitSnapshot(web) { it.optDouble("vh") > 100 }
        assertEquals(state.getDouble("inner"), state.getDouble("vh"), 2.0)
    }
    @Test fun authenticatedLoginRedirectLandsWithDependencies() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveEams") == "true")
        compose.runOnUiThread { compose.activity.setContent { WebLogin("fixture", {}, {}, {}) } }
        val state = awaitSnapshot(browser()) { it.optInt("styles") > 0 && it.optString("jquery") == "function" && it.optString("resize") == "function" }
        println("EAMS CAS redirect structure: $state")
    }
    @Test fun authenticatedEamsRestoresDependenciesAcrossEntryRefreshAndNavigation() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveEams") == "true")
        compose.runOnUiThread { compose.activity.setContent { CampusWeb("eams", {}) } }
        val web = browser()
        fun ready() = awaitSnapshot(web) { !it.optBoolean("old") && it.optInt("styles") > 0 && it.optString("jquery") == "function" && it.optString("resize") == "function" }
        fun assertHomeFrame(state: JSONObject) {
            assertTrue(state.getInt("clientWidth") >= 980)
            val frames = state.getJSONArray("frames")
            val main = (0 until frames.length()).map { frames.getJSONObject(it) }.first { it.optString("path").contains("homeExt!main") }
            assertTrue(main.getInt("styles") >= 7)
            assertTrue(main.getInt("rules") > 500)
        }
        val first = ready()
        assertHomeFrame(first)
        println("EAMS entry structure: $first")
        compose.runOnUiThread { web.evaluateJavascript("window.__xatuRegressionOldDocument=true;location.reload()", null) }
        val refreshed = ready()
        assertHomeFrame(refreshed)
        println("EAMS refresh structure: $refreshed")
        val linkReady = CountDownLatch(1)
        compose.runOnUiThread {
            web.evaluateJavascript("window.__xatuRegressionOldDocument=true;var a=document.createElement('a');a.href='/eams/myPlanCompl.action';a.textContent='Open';a.style='position:fixed;left:0;top:0;width:200px;height:100px;z-index:2147483647;background:white';document.body.appendChild(a)") { linkReady.countDown() }
        }
        assertTrue(linkReady.await(5, TimeUnit.SECONDS))
        val position = IntArray(2)
        compose.runOnUiThread { web.getLocationOnScreen(position) }
        val down = SystemClock.uptimeMillis()
        MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, position[0]+10f, position[1]+10f, 0).let { event ->
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true); event.recycle()
        }
        Thread.sleep(80)
        MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, position[0]+10f, position[1]+10f, 0).let { event ->
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true); event.recycle()
        }
        println("EAMS navigation structure: ${awaitSnapshot(web) { !it.optBoolean("old") && it.optString("path") == "/eams/myPlanCompl.action" && it.optInt("styles") > 0 && it.optString("jquery") == "function" }}")
        compose.runOnUiThread {
            assertTrue(web.canGoBack())
            web.evaluateJavascript("window.__xatuRegressionOldDocument=true", null)
            web.goBack()
        }
        println("EAMS back structure: ${ready()}")
    }
}
