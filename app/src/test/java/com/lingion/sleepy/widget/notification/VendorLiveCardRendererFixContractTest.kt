package com.lingion.sleepy.widget.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cross-checked protocol contract test. Locks the post-review fixes:
 * vivo operation flips 0→1 on update, clickResp is sent, baseInfos.progress
 * is gone (use infos.progress), shortInfos has image/icon, capsule has icon,
 * Meizu capsule has icon + colors, Xiaomi does not auto-expand on every
 * repaint and references the Xiaomi standard promoted-ongoing path.
 */
class VendorLiveCardRendererFixContractTest {

    private val source: String
        get() {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                val file = File(dir, "app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveCardRenderer.kt")
                if (file.isFile) return file.readText()
                dir = dir.parentFile
            }
            error("renderer source not found")
        }

    private val manifestSource: String
        get() {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                val file = File(dir, "app/src/main/AndroidManifest.xml")
                if (file.isFile) return file.readText()
                dir = dir.parentFile
            }
            error("AndroidManifest.xml not found")
        }

    private val supportSource: String
        get() {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                val file = File(
                    dir,
                    "app/src/main/java/com/lingion/sleepy/widget/notification/VendorLiveCardSupport.kt"
                )
                if (file.isFile) return file.readText()
                dir = dir.parentFile
            }
            error("support source not found")
        }

    @Test
    fun `flyme live state permission is declared in manifest`() {
        assertTrue(
            "Meizu live capsule is gated on flymeLiveEnabled which reads the " +
                "Flyme provider — without the permission the gate can never pass",
            manifestSource.contains("flyme.permission.READ_NOTIFICATION_LIVE_STATE")
        )
    }

    @Test
    fun `xiaomi matches v1056 and carries no private focus extras`() {
        assertFalse(source.contains("putString(\"miui.focus"))
        assertFalse(source.contains("putBundle(\"miui.focus"))
        assertFalse(source.contains("private fun addXiaomiExtras"))
        assertTrue(source.contains("LiveCardVendor.XIAOMI -> Unit"))
        assertTrue(source.contains("setRequestPromotedOngoing(true)"))
        assertFalse(
            "Renderer must not reflect private Xiaomi SystemProperties",
            supportSource.contains("SystemProperties")
        )
    }

    @Test
    fun `support suppress lint lives where hidden apis are actually called`() {
        assertTrue(
            "vivoRegisterSceneList reflects NotificationManager — needs BlockedPrivateApi",
            supportSource.contains("@SuppressLint(\"BlockedPrivateApi\")")
        )
        assertFalse(
            "renderer calls no hidden API — must not carry SuppressLint",
            source.contains("SuppressLint")
        )
    }

    @Test
    fun `renderer keeps promoted ongoing Android 16 path`() {
        assertTrue(
            "Promoted ongoing is part of the OPPO baseline lifecycle",
            source.contains("setRequestPromotedOngoing(true)")
        )
    }

    @Test
    fun `flyme live-state permission is declared for meizu capability probe`() {
        var dir: File? = File(".").absoluteFile
        var manifest: File? = null
        while (dir != null) {
            val candidate = File(dir, "app/src/main/AndroidManifest.xml")
            if (candidate.isFile) {
                manifest = candidate
                break
            }
            dir = dir.parentFile
        }
        assertTrue(
            "Meizu live-state probe requires the Flyme permission declaration",
            manifest?.readText()?.contains("flyme.permission.READ_NOTIFICATION_LIVE_STATE") == true
        )
    }

    /** Slice of renderer source belonging to one vendor extras function. */
    private fun vendorBody(name: String): String {
        val start = source.indexOf("private fun $name(")
        assertTrue("renderer must define $name", start >= 0)
        val nextFun = source.indexOf("\n    private fun ", start + 1)
        val end = if (nextFun >= 0) nextFun else source.indexOf("\n}", start)
        return source.substring(start, end.coerceAtLeast(start))
    }

    @Test
    fun `vivo operation covers create update and end`() {
        val vivo = vendorBody("addVivoExtras")
        assertTrue(
            "vivo renderer must emit operation 2 after the class ends",
            vivo.contains("!state.isActive -> 2")
        )
        assertTrue(
            "vivo renderer must switch operation 0 -> 1 while active",
            vivo.contains("state.updateSequence <= 1 -> 0") &&
                vivo.contains("else -> 1")
        )
    }

    @Test
    fun `vivo end-of-life uninstalls the island gracefully`() {
        // origin-isle PROTOCOL §2: on class end, the same id is reposted with
        // showNotify=false + changedRecord=MAX so OriginOS tears the island down
        // rather than leaving a stale card (issue #164 "card flies out" family).
        val vivo = vendorBody("addVivoExtras")
        assertTrue(
            "vivo showNotify must follow isActive so end posts hide the notify",
            vivo.contains("putBoolean(\"notification.superx.showNotify\", state.isActive)")
        )
        assertTrue(
            "vivo changedRecord must jump to Int.MAX_VALUE on end",
            vivo.contains("if (state.isActive) state.updateSequence else Int.MAX_VALUE")
        )
    }

    @Test
    fun `honor injects notification live capsule envelope`() {
        // Bugaoshan PR #368 real-device verified on MagicOS (Android 17): the
        // system extracts notification.live.* extras into a status-bar capsule.
        assertTrue(
            "Honor branch must route to addHonorExtras",
            source.contains("LiveCardVendor.HONOR -> addHonorExtras(")
        )
        val honor = vendorBody("addHonorExtras")
        assertTrue(
            "Honor must set notification.live.operation with the 2/0/1 lifecycle",
            honor.contains("putInt(\n                \"notification.live.operation\"") &&
                honor.contains("!state.isActive -> 2") &&
                honor.contains("state.updateSequence <= 1 -> 0")
        )
        assertTrue(
            "Honor capsule must ship capsuleStatus/capsuleType/title/content",
            honor.contains("notification.live.capsuleStatus") &&
                honor.contains("notification.live.capsuleType") &&
                honor.contains("notification.live.capsuleTitle") &&
                honor.contains("notification.live.capsuleContent")
        )
        assertTrue(
            "Honor must carry event ORDER + type 3 (progress) per verified wire",
            honor.contains("notification.live.event\", \"ORDER\"") &&
                honor.contains("notification.live.type\", 3")
        )
        // HUAWEI stays on the generic path (ArkTS-only Live View, no Android extras).
        assertFalse(
            "Huawei must NOT get a notification.live envelope (no Android implementation path)",
            source.contains("LiveCardVendor.HUAWEI -> add")
        )
    }

    @Test
    fun `vivo registers clickResp as PendingIntent bundle`() {
        assertTrue(
            "vivo renderer must write notification.superx.clickResp",
            source.contains("putParcelable(\"notification.superx.clickResp\", contentIntent)")
        )
    }

    @Test
    fun `vivo progress uses infos progress not baseInfos progress`() {
        assertFalse(
            "vivo renderer must NOT use baseInfos.progress",
            source.contains("notification.superx.baseInfos.progress\"")
        )
        assertTrue(
            "vivo renderer must use infos.progress",
            source.contains("notification.superx.infos.progress\", state.progress")
        )
        assertTrue(
            "vivo renderer must use infos.nodeIcon (template 2 required)",
            source.contains("notification.superx.infos.nodeIcon")
        )
    }

    @Test
    fun `vivo shortInfos includes icon and imageClickResp`() {
        assertTrue(
            "shortInfos.image required",
            source.contains("notification.superx.shortInfos.image\"")
        )
        assertTrue(
            "shortInfos.imageClickResp required",
            source.contains("notification.superx.shortInfos.imageClickResp\"")
        )
    }

    @Test
    fun `vivo atomic island bundle carries templates and progress`() {
        assertTrue(
            "vivo renderer must emit the top-level atomic-island bundle",
            source.contains("putBundle(\"notification.superx.island\"")
        )
        assertTrue(
            "vivo island must define left and right templates",
            source.contains("island.superx.leftTemplate") &&
                source.contains("island.superx.rightTemplate")
        )
        assertTrue(
            "vivo island rightInfo must carry progress value",
            source.contains("island.superx.rightInfo.progressValue")
        )
    }

    @Test
    fun `meizu capsule forces opaque background before luminance`() {
        assertTrue(
            "Meizu capsule must clamp alpha before choosing foreground color",
            source.contains("val opaqueThemeColor = themeColor or 0xFF000000.toInt()")
        )
        assertTrue(
            "Meizu capsule background must use the opaque color",
            source.contains("putInt(\"notification.live.capsuleBgColor\", opaqueThemeColor)")
        )
    }

    @Test
    fun `vivo renderer ships the atomic island not a separate capsule bundle`() {
        // OriginOS exposes two surfaces — a short capsule and the atomic-island card.
        // Sending both wastes IPC budget and forces the system to pick a winner.
        // Lock the renderer to island only.
        assertFalse(
            "vivo renderer must not write notification.superx.capsule alongside the island bundle",
            source.contains("putBundle(\"notification.superx.capsule\"")
        )
        assertTrue(
            "vivo renderer must still ship the atomic island bundle",
            source.contains("putBundle(\"notification.superx.island\"")
        )
    }

    @Test
    fun `vivo island rightInfo carries progress color`() {
        // Corpus (OriginIslandTemplates.kt) emits progressColor whenever a
        // PROGRESS template takes a color. Without it the framework picks
        // its default color and themed brands visually break.
        assertTrue(
            "vivo island rightInfo must thread the theme color into progressColor",
            source.contains("putInt(\"island.superx.rightInfo.progressColor\", themeColor)")
        )
    }

    @Test
    fun `vivo renderer early-returns when scene registration fails`() {
        // If vivo rejects the scene allow-list the island bundle becomes
        // silent noise. Lock the gate so future refactors can't drop it.
        assertTrue(
            "addVivoExtras must early-return when vivoRegisterSceneList returns false",
            source.contains("val registered = support.vivoRegisterSceneList(") &&
                source.contains("if (!registered) return")
        )
    }

    @Test
    fun `vivo island progress content stays short`() {
        assertTrue(
            "vivo island progressContent should use the short room label, not the joined detail text",
            source.contains("putString(\"island.superx.rightInfo.progressContent\", state.room)")
        )
    }

    @Test
    fun `samsung extras use a plain set without merge fallback`() {
        // Samsung is the only vendor branch that writes extras on this code path,
        // so builder.extras is always null — a merge branch is dead code that
        // misleads readers into thinking Samsung shares the bundle with another
        // vendor (it doesn't).
        assertFalse(
            "samsung renderer must not carry a no-op merge branch (builder.extras is null here)",
            source.contains("builder.extras?.apply")
        )
        assertTrue(
            "samsung renderer must set extras directly",
            source.contains("builder.setExtras(samsungExtras)")
        )
    }

    @Test
    fun `meizu capsule icon and colors are sent`() {
        assertTrue(
            "Meizu capsule must ship icon",
            source.contains("putParcelable(\"notification.live.capsuleIcon\"")
        )
        assertTrue(
            "Meizu capsule must ship bg color",
            source.contains("\"notification.live.capsuleBgColor\"")
        )
        assertTrue(
            "Meizu capsule must choose content color from background luminance",
            source.contains("Color.luminance(opaqueThemeColor)")
        )
    }
}
