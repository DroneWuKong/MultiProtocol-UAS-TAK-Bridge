package com.dronewukong.takbridge

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.webkit.WebView
import com.dronewukong.takbridge.ui.MainActivity
import com.dronewukong.takbridge.ui.bridge.WingmanJsBridge
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToolsActionsTest {
    @Test fun nativeCopyPreservesEveryCliLine() {
        val context = RuntimeEnvironment.getApplication()
        val text = "vtxtable powervalues 14 20 26 36\nsave"
        assertTrue(WingmanJsBridge(context).copyText(text))
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(text, clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test fun reportRejectsInvalidNamesAndOversizedPayloads() {
        var calls = 0
        val bridge = WingmanJsBridge(RuntimeEnvironment.getApplication()) { _, _ -> calls++ }
        bridge.saveReport("../report.html", "text")
        bridge.saveReport("report.html", "x".repeat(1_000_001))
        assertEquals(0, calls)
        bridge.saveReport("TAK-Bridge-report.html", "<html>Report</html>")
        assertEquals(1, calls)
    }

    @Test fun reportFromToolHashUsesDocumentPickerAndWritesChosenUri() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val web = activity.supportFragmentManager.findFragmentByTag("tools")!!.requireView() as WebView
            web.loadUrl("https://appassets.androidplatform.net/assets/tools/tools_offline.html#rf-terrain")
            val bridge = shadowOf(web).getJavascriptInterface("Android") as WingmanJsBridge
            val report = "<!DOCTYPE html><html><body>Test terrain report · 915 MHz</body></html>"
            bridge.saveReport("terrain.html", report)
            shadowOf(Looper.getMainLooper()).idle()
            val request = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
            assertEquals("text/html", request.intent.type)
            assertEquals("terrain.html", request.intent.getStringExtra(Intent.EXTRA_TITLE))
            val uri = Uri.parse("content://test.provider/report")
            val closed = CountDownLatch(1)
            val output = object : ByteArrayOutputStream() {
                override fun close() { super.close(); closed.countDown() }
            }
            shadowOf(activity.contentResolver).registerOutputStream(uri, output)
            shadowOf(activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Report was not written", closed.await(5, TimeUnit.SECONDS))
            assertEquals(report, output.toString("UTF-8"))
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test fun cancelledExportCanBeRetried() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val web = activity.supportFragmentManager.findFragmentByTag("tools")!!.requireView() as WebView
            val bridge = shadowOf(web).getJavascriptInterface("Android") as WingmanJsBridge
            bridge.saveReport("first.html", "first")
            shadowOf(Looper.getMainLooper()).idle()
            val first = shadowOf(activity).nextStartedActivityForResult
            shadowOf(activity).receiveResult(first.intent, Activity.RESULT_CANCELED, null)
            bridge.saveReport("second.html", "second")
            shadowOf(Looper.getMainLooper()).idle()
            val second = shadowOf(activity).nextStartedActivityForResult
            assertEquals("second.html", second.intent.getStringExtra(Intent.EXTRA_TITLE))
        }
    }
}
