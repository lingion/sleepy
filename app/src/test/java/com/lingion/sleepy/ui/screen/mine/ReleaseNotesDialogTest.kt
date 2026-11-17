package com.lingion.sleepy.ui.screen.mine

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.component.liquidglass.createSoftwareWindowComposeRule
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.util.MarkdownBlocks
import com.lingion.sleepy.util.ReleaseNotesCandidate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h640dp-mdpi")
class ReleaseNotesDialogTest {
    @get:Rule val compose = createSoftwareWindowComposeRule()

    @Test fun classicShowsCompleteReleaseAndDownloads() = checkDialog(UiStyle.Classic)
    @Test fun glassShowsCompleteReleaseAndDownloads() = checkDialog(UiStyle.LiquidGlass)

    private fun checkDialog(style: UiStyle) {
        val info = ReleaseNotesCandidate.info()
        var downloaded: Triple<String, String, String>? = null
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost(Modifier.fillMaxSize(), background = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        UpdateChangelogDialog(
                            UpdateUiState.UpdateAvailable(info.version, info.changelog, info.downloadUrl),
                            onDismiss = {}, onDownload = { version, body, url -> downloaded = Triple(version, body, url) },
                            onCancelDownload = {}, onRetry = { _, _, _ -> },
                        )
                    }
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithText(context.getString(R.string.update_found_title, info.version)).assertIsDisplayed()
        capture("${style.name}-start")
        // Reach every paragraph and bullet in order, including the final Chinese verification entry.
        MarkdownBlocks.parse(info.changelog).flatMap { block -> when (block) {
            is MarkdownBlocks.Block.Bullet -> block.items
            is MarkdownBlocks.Block.Paragraph -> listOf(block.text)
            is MarkdownBlocks.Block.Heading -> emptyList()
        } }.forEach { text ->
            compose.onNodeWithText(ReleaseNotesCandidate.visible(text))
                .performScrollTo().assertIsDisplayed()
        }
        capture("${style.name}-end")
        compose.onNodeWithText(context.getString(R.string.update_download)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Triple(info.version, info.changelog, info.downloadUrl), downloaded) }
    }

    private fun capture(name: String) {
        val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        val file = File("build/reports/release-notes/$name.png")
        file.parentFile.mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
