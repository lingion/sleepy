package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.material3.ModalBottomSheet as MaterialBottomSheet

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class GlassBottomSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun classicHalfHeightSheetDrawsClosesAndReopens() = halfHeightSheet(UiStyle.Classic)

    @Test fun glassHalfHeightSheetDrawsClosesAndReopens() = halfHeightSheet(UiStyle.LiquidGlass)

    @Test fun glassKeepsMaterialGeometryWithHandleAndBottomInsets() = materialGeometry(withHandle = true)

    @Test fun glassKeepsMaterialGeometryWithoutHandle() = materialGeometry(withHandle = false)

    private fun materialGeometry(withHandle: Boolean) {
        val useGlassWrapper = mutableStateOf(false)
        val handle: (@Composable () -> Unit)? = if (withHandle) {
            { Box(Modifier.size(80.dp, 48.dp).background(Color.Green).testTag("custom-handle")) }
        } else null
        val body: @Composable ColumnScope.() -> Unit = {
            Box(Modifier.fillMaxWidth().height(300.dp).background(Color.Magenta).testTag("geometry-content"))
        }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(Modifier.fillMaxSize()) {
                        if (useGlassWrapper.value) {
                            GlassModalBottomSheet(
                                onDismissRequest = {}, dragHandle = handle,
                                contentWindowInsets = { WindowInsets(bottom = 80) }, content = body,
                            )
                        } else {
                            MaterialBottomSheet(
                                onDismissRequest = {}, dragHandle = handle,
                                contentWindowInsets = { WindowInsets(bottom = 80) }, content = body,
                            )
                        }
                    }
                }
            }
        }
        val materialBounds = compose.onNodeWithTag("geometry-content").fetchSemanticsNode().boundsInRoot
        val materialHandle = if (withHandle) compose.onNodeWithTag("custom-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot else null
        compose.runOnIdle { useGlassWrapper.value = true }
        compose.onNodeWithTag("geometry-content").assertIsDisplayed()
        assertEquals("wrapper must retain Material's height, offset and inset consumption", materialBounds,
            compose.onNodeWithTag("geometry-content").fetchSemanticsNode().boundsInRoot)
        if (withHandle) {
            assertEquals(materialHandle, compose.onNodeWithTag("custom-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
            val image = compose.onNodeWithTag("custom-handle", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            assertEquals("native drag handle must be drawn too", android.graphics.Color.GREEN,
                image.getPixel(image.width / 2, image.height / 2))
        }
    }

    private fun halfHeightSheet(style: UiStyle) {
        val open = mutableStateOf(true)
        val menuOpen = mutableStateOf(false)
        var sheetScene: GlassHostState? = null
        var menuSource: GlassHostState? = null
        var dismissals = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost(Modifier.fillMaxSize(), background = Color.White) {
                        if (open.value) {
                            GlassModalBottomSheet(
                                onDismissRequest = { dismissals++; open.value = false },
                                sheetState = rememberBottomSheetState(
                                    initialValue = SheetValue.Hidden,
                                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                                ),
                                modifier = Modifier.testTag("sheet"),
                            ) {
                                sheetScene = LocalGlassPopupScene.current
                                Box(Modifier.fillMaxWidth().height(300.dp).testTag("sheet-content")) {
                                    Box(Modifier.size(80.dp).background(Color.Magenta).testTag("visible-marker"))
                                    if (menuOpen.value) {
                                        GlassDropdownMenu(expanded = true, onDismissRequest = { menuOpen.value = false }) {
                                            menuSource = LocalGlassOverlaySamplingHost.current
                                            Text("Nested sheet menu")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        repeat(2) { opening ->
            compose.onNodeWithTag("sheet-content").assertIsDisplayed()
            val bounds = compose.onNodeWithTag("sheet-content").fetchSemanticsNode().boundsInRoot
            assertEquals("half-height content must retain its intrinsic height", 300f, bounds.height, 1f)
            assertTrue("sheet must remain bottom aligned, not stretched to full screen", bounds.top > 300f)
            val image = compose.onNodeWithTag("visible-marker").captureToImage().asAndroidBitmap()
            assertEquals(
                "$style opening $opening: semantic visibility must also produce foreground pixels",
                android.graphics.Color.MAGENTA,
                image.getPixel(image.width / 2, image.height / 2),
            )

            compose.runOnIdle { menuOpen.value = true }
            compose.onNodeWithText("Nested sheet menu").assertIsDisplayed()
            compose.runOnIdle {
                assertNotNull(sheetScene)
                assertSame("nested menu samples the sheet, not the route", sheetScene, menuSource)
                menuOpen.value = false
            }
            compose.onNodeWithTag("sheet").performTouchInput { swipeDown(durationMillis = 150) }
            compose.waitForIdle()
            compose.onNodeWithTag("sheet-content").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(opening + 1, dismissals)
                if (opening == 0) open.value = true
            }
        }
    }
}
