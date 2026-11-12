package com.lingion.sleepy.widget

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lingion.sleepy.ui.screen.widget.WideGridPreviewContent
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** All honest preview states retain the fixed component layout without introducing a shape selector. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h800dp-port-mdpi", application = com.lingion.sleepy.SleepyApp::class)
class WeekGridWideEmpiricalStressTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allPreviewStatusesKeepTheFixedLayoutAndNeverOfferOriginalOrOtherModes() {
        var status by mutableStateOf(WideGridPreviewStatus.LOADING)
        val bitmap = Bitmap.createBitmap(300, 160, Bitmap.Config.ARGB_8888)
        compose.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                WideGridPreviewContent(WeekGridWideSettings(), WideGridPreviews(status,
                    if (status == WideGridPreviewStatus.READY) listOf(
                        WideGridPreview(WeekGridWideMode.VERTICAL, WeekData(emptyList(), true), bitmap)) else emptyList()),
                    onLabelSideChange = {})
            }
        }
        for (state in WideGridPreviewStatus.entries) {
            compose.runOnIdle { status = state }
            compose.onNodeWithTag("wide-fixed-layout-VERTICAL").assertExists()
            for (mode in listOf("ORIGINAL", "VERTICAL", "HORIZONTAL"))
                compose.onNodeWithTag("wide-shape-$mode").assertDoesNotExist()
            if (state == WideGridPreviewStatus.READY) {
                compose.onNodeWithTag("wide-preview-status").assertDoesNotExist()
                compose.onNodeWithTag("wide-preview-VERTICAL", useUnmergedTree = true).assertExists()
            } else compose.onNodeWithTag("wide-preview-status").assertExists()
            compose.onNodeWithTag("wide-label-LEFT").assertDoesNotExist()
        }
    }
}
