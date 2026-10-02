package com.dronewukong.takbridge

import com.dronewukong.takbridge.transport.ConnectionProfile
import com.dronewukong.takbridge.transport.ConfigStore
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionProfileMigrationTest {
    @Test fun savedProfilesSurviveLabelAndKeyChanges() {
        val context = RuntimeEnvironment.getApplication()
        // Same preference file used by ConfigStore.
        val prefs = context.getSharedPreferences("tak_bridge_config", Context.MODE_PRIVATE)
        for ((stored, expected) in listOf(
            "TAC_MAVLINK" to ConnectionProfile.CONTROLLER_MAVLINK,
            "TAC_GHST" to ConnectionProfile.CONTROLLER_GHST
        )) {
            prefs.edit().putString("connection_profile", stored).commit()
            assertEquals(expected, ConfigStore.loadConnectionProfile(context))
            assertEquals(expected, ConnectionProfile.fromPersistedName(stored))
            assertTrue(expected.dtr)
            assertFalse(expected.pollsMsp)
        }
        for (profile in ConnectionProfile.entries) {
            ConfigStore.saveConnectionProfile(context, profile, profile.dtr)
            assertEquals(profile, ConfigStore.loadConnectionProfile(context))
        }
        prefs.edit().clear().commit()
        assertEquals(ConnectionProfile.CONTROLLER_MAVLINK, ConfigStore.loadConnectionProfile(context))
        assertEquals(ConnectionProfile.CONTROLLER_MAVLINK, ConnectionProfile.fromPersistedName(null))
        assertEquals(ConnectionProfile.CONTROLLER_MAVLINK, ConnectionProfile.fromPersistedName("unknown"))
    }
}
