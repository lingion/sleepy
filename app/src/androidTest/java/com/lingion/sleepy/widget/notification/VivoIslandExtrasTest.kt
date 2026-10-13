package com.lingion.sleepy.widget.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.lingion.sleepy.MainActivity
import org.junit.Assert.*
import org.junit.Test

/** Executes the production renderer on Android; validates the final parcelable payload. */
class VivoIslandExtrasTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun build(vendor: LiveCardVendor = LiveCardVendor.VIVO, sequence: Int = 1,
                      now: Long = 100L, registered: Boolean = true) = VendorLiveCardRenderer.build(
        context, CourseLiveCardState("Math", "A101", "Teacher", "09:00", 0L, 1000L, now, sequence),
        PendingIntent.getActivity(context, 164, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        "vivo_protocol_test", vendor, object : VendorLiveCardRenderer.SupportProbes {
            override fun flymeLiveEnabled(ctx: Context) = false
            override fun flymeVersion() = 0
            override fun samsungNowBarFeature(ctx: Context) = false
            override fun vivoRegisterSceneList(ctx: Context, scenes: List<String>) = registered
        })

    @Test fun vivoAndIqooDeclareExpandedCardAndCorrectClickKey() {
        for (vendor in listOf(LiveCardVendor.VIVO, LiveCardVendor.IQOO)) {
            val extras = build(vendor).extras
            val island = extras.getBundle("notification.superx.island")!!
            assertEquals(2, island.get("island.superx.template"))
            assertEquals(0, island.get("island.superx.islandClick"))
            assertFalse(island.containsKey("island.superx.click"))
            assertTrue(island.get("island.superx.clickResp") is PendingIntent)
            assertEquals(1, island.get("island.superx.leftTemplate"))
            assertEquals(2, island.get("island.superx.rightTemplate"))
            // Official fallback: no island-specific data means reuse notification data.
            assertFalse(island.containsKey("island.superx.baseInfos"))
            val base = extras.getBundle("notification.superx.baseInfos")!!
            assertEquals("Math", base.getCharSequence("notification.superx.baseInfos.title"))
            val infos = extras.getBundle("notification.superx.infos")!!
            assertTrue(infos.get("notification.superx.infos.progress") is Int)
            val icons = infos.getParcelableArrayList<Icon>("notification.superx.infos.nodeIcon")!!
            assertEquals(2, icons.size)
        }
    }

    @Test fun capsuleFallbackCoexistsWithIsland() {
        val extras = build().extras
        val capsule = extras.getBundle("notification.superx.capsule")!!
        assertNotNull(extras.getBundle("notification.superx.island"))
        assertEquals(1, capsule.get("notification.superx.capsule.state"))
        assertTrue(capsule.get("notification.superx.capsule.icon") is Icon)
        assertTrue(capsule.get("notification.superx.capsule.clickResp") is PendingIntent)
    }

    @Test fun createUpdateAndEndRemainTypedOperations() {
        assertEquals(0, build(sequence = 1).extras.get("notification.superx.operation"))
        assertEquals(1, build(sequence = 2).extras.get("notification.superx.operation"))
        assertEquals(2, build(sequence = 3, now = 1000L).extras.get("notification.superx.operation"))
    }

    @Test fun rejectedProbeKeepsStandardNotification() {
        val notification = build(registered = false)
        assertFalse(notification.extras.containsKey("notification.superx.island"))
        assertEquals("Math", notification.extras.getCharSequence("android.title"))
        assertNotNull(notification.contentIntent)
    }

    @Test fun genericRendererDoesNotAcquireVivoPayload() {
        assertFalse(build(LiveCardVendor.GENERIC).extras.containsKey("notification.superx.island"))
    }
}

