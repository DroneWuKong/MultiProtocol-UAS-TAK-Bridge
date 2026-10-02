package com.dronewukong.takbridge

import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import android.widget.ImageView
import android.view.View
import android.view.ViewGroup
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import java.io.File
import com.dronewukong.takbridge.ui.MainActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercise the actual manifest, layout inflation and startup without USB hardware. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStartupTest {
    @Test fun coldLaunchShowsConnectionControlsWithoutHardware() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            assertTrue(activity.findViewById<Button>(R.id.btnConnect).isEnabled)
            assertTrue(activity.findViewById<CheckBox>(R.id.checkDtr).isChecked)
            assertEquals("USB controller · MAVLink transcode",
                activity.findViewById<Spinner>(R.id.spinnerConnectionProfile).selectedItem)
        }
    }

    @Test fun connectWithoutUsbShowsExplanationAndKeepsAppOpen() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            activity.findViewById<Button>(R.id.btnConnect).performClick()
            assertTrue(activity.findViewById<TextView>(R.id.statusBar).text
                .startsWith("No USB serial interface"))
            assertFalse(activity.isFinishing)
        }
    }

    @Test fun recreationRestoresFragmentsAndNavigationWithoutDuplicates() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val first = controller.setup().get()
            first.findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_tools
            first.supportFragmentManager.executePendingTransactions()

            controller.recreate()
            val restored = controller.get()
            assertEquals(2, restored.supportFragmentManager.fragments.size)
            assertFalse(restored.supportFragmentManager.findFragmentByTag("tools")!!.isHidden)
            restored.findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_map
            restored.supportFragmentManager.executePendingTransactions()
            assertFalse(restored.supportFragmentManager.findFragmentByTag("map")!!.isHidden)
            assertTrue(restored.supportFragmentManager.findFragmentByTag("tools")!!.isHidden)
            assertTrue(restored.findViewById<Button>(R.id.btnConnect).isShown)
        }
    }

    @Test fun toolsLoadBundledAssetsFromHttpsOrigin() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val web = activity.supportFragmentManager.findFragmentByTag("tools")!!.requireView() as WebView
            assertEquals("https://appassets.androidplatform.net/assets/tools/tools_offline.html", web.url)
            assertFalse(web.settings.allowFileAccess)
            assertFalse(web.settings.loadWithOverviewMode)
            assertTrue(activity.assets.open("tools/vendor/leaflet/leaflet.js").use { it.read() } >= 0)
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun bottomTabsKeepIconsLabelsAndContentSeparate() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val root = activity.findViewById<ViewGroup>(R.id.appRoot)
            val density = activity.resources.displayMetrics.density
            val width = (393 * density).toInt()
            val height = (800 * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val nav = activity.findViewById<BottomNavigationView>(R.id.bottomNav)
            assertTrue(activity.findViewById<View>(R.id.fragmentContainer).bottom <= nav.top)
            fun descendants(view: View): List<View> = listOf(view) +
                if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            val menu = nav.getChildAt(0) as ViewGroup
            for (i in 0 until menu.childCount) {
                val views = descendants(menu.getChildAt(i))
                val icon = views.filterIsInstance<ImageView>().first { it.isShown }
                val label = views.filterIsInstance<TextView>().first { it.isShown && it.text.isNotBlank() }
                val iconRect = Rect(); val labelRect = Rect()
                icon.getGlobalVisibleRect(iconRect); label.getGlobalVisibleRect(labelRect)
                assertTrue("Tab icon overlaps ${label.text}", iconRect.bottom <= labelRect.top)
            }
            val replay = activity.findViewById<Button>(R.id.btnReplay)
            val diagnostics = activity.findViewById<Button>(R.id.btnDiagnostics)
            assertTrue(replay.isShown && diagnostics.isShown)
            val replayBounds = Rect(); val diagnosticBounds = Rect()
            replay.getGlobalVisibleRect(replayBounds); diagnostics.getGlobalVisibleRect(diagnosticBounds)
            assertTrue(replayBounds.right <= diagnosticBounds.left)
            assertTrue(diagnosticBounds.bottom <= nav.top)
            val whole = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(whole))
            val screen = File("build/reports/ui/map-controls.png")
            screen.parentFile?.mkdirs()
            screen.outputStream().use { whole.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Render the real Android navigation component for visual review.
            val bitmap = Bitmap.createBitmap(width, nav.height, Bitmap.Config.ARGB_8888)
            nav.draw(Canvas(bitmap))
            val output = File("build/reports/ui/bottom-navigation.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun connectionSettingsRemainReachableAtPhoneLandscapeAndTabletSizes() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            val root = activity.findViewById<ViewGroup>(R.id.appRoot)
            val density = activity.resources.displayMetrics.density
            fun layout(w: Int, h: Int) {
                repeat(3) {
                    val width = (w * density).toInt(); val height = (h * density).toInt()
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, width, height)
                }
            }
            layout(393, 800)
            val toggle = activity.findViewById<Button>(R.id.btnConnectionSettings)
            toggle.performClick()
            for ((w, h) in listOf(360 to 640, 800 to 360, 800 to 1100, 393 to 800)) {
                layout(w, h)
                val settings = activity.findViewById<android.widget.ScrollView>(R.id.connectionSettings)
                val nav = activity.findViewById<View>(R.id.bottomNav)
                val panel = activity.findViewById<View>(R.id.bottomPanel)
                // The test sizes the app root independently of Robolectric's decor window.
                // Check its actual parent geometry, not the default decor's clipping rect.
                val frame = panel.parent as View
                assertTrue("Settings panel clipped at $w x $h: ${panel.top}..${panel.bottom}/${frame.height}",
                    panel.top >= 0 && panel.bottom <= frame.height)
                assertTrue(settings.isShown && settings.height > 0)
                assertTrue(toggle.height >= (44 * density).toInt())
                val connect = activity.findViewById<Button>(R.id.btnConnect)
                val actionRow = connect.parent as View
                assertTrue("Connect clipped within its action row at $w x $h", connect.top >= 0 && connect.bottom <= actionRow.height)
                assertTrue("Action row clipped within settings panel at $w x $h", actionRow.top >= 0 && actionRow.bottom <= panel.height)
                assertTrue("Map content overlaps tabs at $w x $h", activity.findViewById<View>(R.id.fragmentContainer).bottom <= nav.top)
                settings.isSmoothScrollingEnabled = false
                settings.fullScroll(View.FOCUS_DOWN)
                val local = activity.findViewById<CheckBox>(R.id.checkMulticast)
                val localBounds = Rect(0, 0, local.width, local.height)
                settings.offsetDescendantRectToMyCoords(local, localBounds)
                localBounds.offset(0, -settings.scrollY)
                assertTrue("Local TAK unreachable at $w x $h: $localBounds / ${settings.height}",
                    localBounds.top >= 0 && localBounds.bottom <= settings.height)
                val render = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(render))
                File("build/reports/ui/settings-${w}x${h}.png").apply { parentFile?.mkdirs() }.outputStream().use {
                    render.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            val settings = activity.findViewById<android.widget.ScrollView>(R.id.connectionSettings)
            settings.scrollTo(0, 0)
            val screenshot = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(screenshot))
            File("build/reports/ui/connection-settings.png").apply { parentFile?.mkdirs() }.outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            controller.recreate()
            assertEquals(View.VISIBLE, controller.get().findViewById<View>(R.id.connectionSettings).visibility)
            controller.get().findViewById<Button>(R.id.btnConnectionSettings).performClick()
            assertEquals(View.GONE, controller.get().findViewById<View>(R.id.connectionSettings).visibility)
        }
    }

}
