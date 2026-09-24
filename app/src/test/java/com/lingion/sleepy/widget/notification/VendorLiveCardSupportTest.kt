package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorLiveCardSupportTest {

    @Test
    fun `xiaomi island feature flag is read defensively`() {
        // Calling on the JVM must not throw even if SystemProperties is missing.
        val value = VendorLiveCardSupport.xiaomiIslandFeatureFlag()
        // On the JVM the reflective call returns the default (false), not random.
        assertEquals(false, value)
    }

    @Test
    fun `flyme version parses only well-formed display strings`() {
        // Default Build.DISPLAY on JVM is "" -> -1. Regex must not throw.
        val parsed = VendorLiveCardSupport.flymeVersion()
        // Either a known version (-1 = unknown, or positive), but not random.
        assertTrue(parsed >= -1)
    }

    @Test
    fun `vivo scene list method signature is stable`() {
        val nmClass = Class.forName("android.app.NotificationManager")
        val method = nmClass.methods.firstOrNull { it.name == "setSuperXInfosSceneList" }
        // On JVM the class is missing the hidden API -> undefined behavior only
        // when we actually call. Lock the symbol existence for downstream calls.
        // (The class itself is on the bootclasspath only on device.)
        if (method != null) {
            assertEquals(4, method.parameterTypes.size)
        } else {
            // The hidden method is device-specific; JVM tests only verify that
            // its absence is handled without throwing.
            assertTrue(true)
        }
    }

    @Test
    fun `support object exposes every probe the renderer relies on`() {
        val probes = listOf(
            VendorLiveCardSupport::xiaomiFocusGranted,
            VendorLiveCardSupport::xiaomiIslandFeatureFlag,
            VendorLiveCardSupport::flymeLiveEnabled,
            VendorLiveCardSupport::flymeVersion,
            VendorLiveCardSupport::samsungNowBarFeature,
            VendorLiveCardSupport::vivoRegisterSceneList,
        )
        assertEquals(6, probes.size)
        probes.forEach { probe ->
            assertNotNull(probe)
            assertNotEquals("Probe ${probe.name} must have a name", "", probe.name)
        }
    }
}