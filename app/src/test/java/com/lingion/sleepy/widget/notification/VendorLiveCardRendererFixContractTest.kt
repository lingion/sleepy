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
 * repaint and references miui.focus.pics.
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
    fun `support suppress lint lives where hidden apis are actually called`() {
        assertTrue(
            "xiaomiIslandFeatureFlag reflects SystemProperties — needs BlockedPrivateApi",
            supportSource.contains("@SuppressLint(\"BlockedPrivateApi\")")
        )
        assertTrue(
            "vivoRegisterSceneList reflects NotificationManager — needs BlockedPrivateApi",
            // Count occurrences: at least 2 (xiaomi + vivo).
            supportSource.windowed("@SuppressLint(\"BlockedPrivateApi\")".length).count { it == "@SuppressLint(\"BlockedPrivateApi\")" } >= 2
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
    fun `xiaomi disables auto-expand on every update`() {
        assertTrue(
            "Xiaomi must not auto-expand on every repaint (causes 15s expand churn)",
            source.contains("put(\"enableFloat\", false)")
        )
        assertTrue(
            "Xiaomi should still allow first-shot expand via islandFirstFloat",
            source.contains("put(\"islandFirstFloat\", true)")
        )
    }

    @Test
    fun `xiaomi ships pics bundle so bigIslandArea picInfo resolves`() {
        assertTrue(
            "Xiaomi renderer must register miui.focus.pics",
            source.contains("\"miui.focus.pics\"")
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

    @Test
    fun `vivo operation covers create update and end`() {
        assertTrue(
            "vivo renderer must emit operation 2 after the class ends",
            source.contains("!state.isActive -> 2")
        )
        assertTrue(
            "vivo renderer must switch operation 0 -> 1 while active",
            source.contains("state.updateSequence <= 1 -> 0") &&
                source.contains("else -> 1")
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
    fun `xiaomi big island includes top-level pic info sibling`() {
        val bigIsland = source.substringAfter("put(\"bigIslandArea\"").substringBefore("put(\"smallIslandArea\"")
        assertTrue(
            "Xiaomi bigIslandArea must have a top-level picInfo sibling",
            bigIsland.contains("put(\"picInfo\"")
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
