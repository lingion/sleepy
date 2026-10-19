package com.lingion.sleepy.golden

import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Shared pixel-golden record/verify harness for JVM tests (Robolectric NATIVE).
 *
 * Record:  GOLDEN_RECORD=1 ./gradlew :app:testDebugUnitTest --tests ...
 * Verify:  plain testDebugUnitTest (CI mode) — fails if diff ratio exceeds max.
 *
 * Baselines live under app/src/test/golden/, resolved via the sleepy.test.root
 * system property wired in app/build.gradle.kts.
 */
object GoldenCompare {
    // Per-channel tolerance + max differing-pixel ratio. 8/2% absorbs antialias
    // noise across toolchain bumps; real layout regressions blow far past it.
    const val CHANNEL_TOLERANCE = 8
    const val MAX_DIFF_RATIO = 0.02

    fun assertGolden(rendered: Bitmap, name: String) {
        val png = ByteArrayOutputStream().use { out ->
            rendered.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val root = System.getProperty("sleepy.test.root") ?: error("sleepy.test.root unset")
        val golden = File(root, "app/src/test/golden/$name")
        if (System.getenv("GOLDEN_RECORD") == "1") {
            golden.parentFile.mkdirs()
            golden.writeBytes(png)
            return
        }
        assertTrue("missing golden $golden — run with GOLDEN_RECORD=1", golden.exists())
        val expected = ImageIO.read(golden)
        val actual = ImageIO.read(java.io.ByteArrayInputStream(png))
        if (expected.width != actual.width || expected.height != actual.height) {
            fail("size drift: golden ${expected.width}x${expected.height}, rendered ${actual.width}x${actual.height}")
        }
        var diff = 0
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                val a = actual.getRGB(x, y)
                val b = expected.getRGB(x, y)
                val far = (Math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) > CHANNEL_TOLERANCE) ||
                    (Math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) > CHANNEL_TOLERANCE) ||
                    (Math.abs((a and 0xFF) - (b and 0xFF)) > CHANNEL_TOLERANCE)
                if (far) diff++
            }
        }
        val ratio = diff.toDouble() / (actual.width * actual.height)
        if (ratio > MAX_DIFF_RATIO) {
            val dump = File(System.getProperty("java.io.tmpdir"), "golden-actual-$name")
            ImageIO.write(actual, "png", dump)
            fail("pixel diff ${"%.2f".format(ratio * 100)}% > threshold on $name (actual dumped to $dump)")
        }
    }
}
