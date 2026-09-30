package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level contracts for vendor-specific extras. Robolectric is intentionally
 * avoided — these tests assert the JSON key paths we ship so future refactors
 * cannot silently remove a documented field.
 */
class VendorLiveCardRendererExtrasTest {

    private val rendererSource: String
        get() {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                val file = File(dir, "app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveCardRenderer.kt")
                if (file.isFile) return file.readText()
                dir = dir.parentFile
            }
            error("renderer source not found")
        }

    @Test
    fun `xiaomi keeps the v1056 promoted ongoing path free of vendor extras`() {
        assertFalse(
            "Xiaomi renderer must not attach Focus extras to the v1.0.56 path",
            rendererSource.contains("putString(\"miui.focus") ||
                rendererSource.contains("putBundle(\"miui.focus")
        )
        assertFalse(
            "Xiaomi renderer must not define a vendor-specific extras builder",
            rendererSource.contains("private fun addXiaomiExtras")
        )
        assertTrue(rendererSource.contains("LiveCardVendor.XIAOMI -> Unit"))
        assertTrue(rendererSource.contains("setRequestPromotedOngoing(true)"))
        assertTrue(rendererSource.contains("ProgressStyle"))
    }

    @Test
    fun `vivo extras use notification superx envelope`() {
        assertTrue(
            "vivo renderer must set notification.superx.operation",
            rendererSource.contains("\"notification.superx.operation\"")
        )
        assertTrue(
            "vivo renderer must post NAVIGATION scene (the only confirmed vivo allow-list scene)",
            rendererSource.contains("\"NAVIGATION\"")
        )
        assertTrue(
            "vivo renderer must set template 2 (progress)",
            rendererSource.contains("\"notification.superx.template\", 2")
        )
        assertTrue(
            "vivo renderer must include changedRecord to guard ordering",
            rendererSource.contains("\"notification.superx.changedRecord\"")
        )
        // METTING/TIMER/TRAIN are still *requested* in scene registration
        // (harmless extras that vivo may or may not grant); only NAVIGATION
        // is posted on the actual notification.
        assertTrue(
            "vivo scene registration should still request the community-known scene list",
            rendererSource.contains("listOf(\"NAVIGATION\", \"METTING\", \"TIMER\", \"TRAIN\")")
        )
    }

    @Test
    fun `meizu extras use notification live capsule keys`() {
        assertTrue(
            "Meizu renderer must flip is_live switch",
            rendererSource.contains("putBoolean(\"is_live\", true)")
        )
        assertTrue(
            "Meizu renderer must set capsuleStatus",
            rendererSource.contains("\"notification.live.capsuleStatus\"")
        )
        assertTrue(
            "Meizu renderer must set capsuleType",
            rendererSource.contains("\"notification.live.capsuleType\"")
        )
    }

    @Test
    fun `huawei honor and oppo oneplus realme do not fabricate SDK calls`() {
        assertTrue(
            "Renderer must not reference HarmonyOS Live View Kit classes",
            !rendererSource.contains("liveViewManager")
        )
        assertTrue(
            "Renderer must not reference Honor Suggestions Kit classes",
            !rendererSource.contains("SuggestionsKit")
        )
        assertTrue(
            "Renderer must not reference OPPO UPK config.json classes",
            !rendererSource.contains("SeedlingCard")
        )
    }
}
