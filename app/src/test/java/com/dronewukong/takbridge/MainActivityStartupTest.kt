package com.dronewukong.takbridge

import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import com.dronewukong.takbridge.ui.MainActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise the actual manifest, layout inflation and startup without USB hardware. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStartupTest {
    @Test fun coldLaunchShowsConnectionControlsWithoutHardware() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            assertTrue(activity.findViewById<Button>(R.id.btnConnect).isEnabled)
            assertTrue(activity.findViewById<CheckBox>(R.id.checkDtr).isChecked)
            assertEquals("TAC.CTRL · MAVLink transcode",
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
}
