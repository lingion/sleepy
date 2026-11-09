package com.lingion.sleepy.widget.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.os.BundleCompat
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
            val notification = build(vendor)
            val extras = notification.extras
            val island = extras.getBundle("notification.superx.island")!!
            assertEquals(4, island.getInt("island.superx.template", -1))
            assertEquals(0, island.getInt("island.superx.islandClick", -1))
            assertFalse(island.containsKey("island.superx.click"))
            assertNotNull(BundleCompat.getParcelable(island, "island.superx.clickResp", PendingIntent::class.java))
            assertEquals(1, island.getInt("island.superx.leftTemplate", -1))
            assertEquals(2, island.getInt("island.superx.rightTemplate", -1))
            // Expanded island uses its own basic template, independent of progress nodes.
            val islandBase = island.getBundle("island.superx.baseInfos")!!
            assertEquals("Math", islandBase.getCharSequence("notification.superx.baseInfos.title"))
            assertEquals("09:00  ·  A101  ·  Teacher", islandBase.getCharSequence("notification.superx.baseInfos.content"))
            assertNotNull(BundleCompat.getParcelable(islandBase, "notification.superx.baseInfos.icon", Icon::class.java))
            assertEquals(5, islandBase.getInt("notification.superx.baseInfos.subInfo", -1))
            assertEquals(10, islandBase.getInt("notification.superx.baseInfos.subProgress", -1))
            assertEquals(0, islandBase.getInt("notification.superx.baseInfos.progressState", -1))
            assertEquals(notification.color, islandBase.getInt("notification.superx.baseInfos.subProgressColor", notification.color.inv()))
            assertFalse(island.containsKey("island.superx.infos"))
            assertFalse(islandBase.containsKey("notification.superx.infos.nodeIcon"))
            // Notification center retains the existing progress visualization.
            assertEquals(2, extras.getInt("notification.superx.template", -1))
            val base = extras.getBundle("notification.superx.baseInfos")!!
            assertEquals("Math", base.getCharSequence("notification.superx.baseInfos.title"))
            val infos = extras.getBundle("notification.superx.infos")!!
            assertEquals(10, infos.getInt("notification.superx.infos.progress", -1))
            val icons = BundleCompat.getParcelableArrayList(infos, "notification.superx.infos.nodeIcon", Icon::class.java)!!
            assertEquals(2, icons.size)
            val right = island.getBundle("island.superx.rightInfo")!!
            assertEquals(10, right.getInt("island.superx.rightInfo.progressValue", -1))
            assertEquals(0, right.getInt("island.superx.rightInfo.progressState", -1))
        }
    }

    @Test fun expandedBasicCardProgressUpdatesAndEnds() {
        for ((now, progress, progressState) in listOf(
            Triple(100L, 10, 0), Triple(500L, 50, 0), Triple(1000L, 100, 1)
        )) {
            val base = build(sequence = 2, now = now).extras
                .getBundle("notification.superx.island")!!
                .getBundle("island.superx.baseInfos")!!
            assertEquals(progress, base.getInt("notification.superx.baseInfos.subProgress", -1))
            assertEquals(progressState, base.getInt("notification.superx.baseInfos.progressState", -1))
        }
    }

    @Test fun capsuleFallbackCoexistsWithIsland() {
        val extras = build().extras
        val capsule = extras.getBundle("notification.superx.capsule")!!
        assertNotNull(extras.getBundle("notification.superx.island"))
        assertEquals(1, capsule.getInt("notification.superx.capsule.state", -1))
        assertNotNull(BundleCompat.getParcelable(capsule, "notification.superx.capsule.icon", Icon::class.java))
        assertNotNull(BundleCompat.getParcelable(capsule, "notification.superx.capsule.clickResp", PendingIntent::class.java))
    }

    @Test fun createUpdateAndEndRemainTypedOperations() {
        assertEquals(0, build(sequence = 1).extras.getInt("notification.superx.operation", -1))
        assertEquals(1, build(sequence = 2).extras.getInt("notification.superx.operation", -1))
        assertEquals(2, build(sequence = 3, now = 1000L).extras.getInt("notification.superx.operation", -1))
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

