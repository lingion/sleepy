package com.lingion.sleepy.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.lingion.sleepy.ui.screen.imports.SchoolSelectScreen
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose-path pixel golden for the import flow's school-picker screen.
 * JwImportViewModel loads schools.json from assets (no network, no Room),
 * and the school list renders in a height-bounded LazyColumn — a stable
 * capture surface. Location state stays Idle (never triggered).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rUS-w360dp-h640dp-port-xhdpi")
class SchoolSelectGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun schoolSelectMatchesGolden() {
        composeRule.setContent {
            SleepyThemeProvider(darkTheme = false, themeKey = "default") {
                Box(modifier = Modifier.fillMaxSize()) {
                    SchoolSelectScreen(
                        onSchoolSelected = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        GoldenCompare.assertGolden(bitmap, "school_select_light.png")
        bitmap.recycle()
    }
}
