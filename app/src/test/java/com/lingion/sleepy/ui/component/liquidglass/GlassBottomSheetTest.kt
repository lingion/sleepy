package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
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

    @Test fun classicKeepsMaterialGeometryWithHandleAndBottomInsets() = materialGeometry(UiStyle.Classic, withHandle = true)

    @Test fun classicKeepsMaterialGeometryWithoutHandle() = materialGeometry(UiStyle.Classic, withHandle = false)

    @Test fun glassInsetsItsCarrierWithHandleAndBottomInsets() = materialGeometry(UiStyle.LiquidGlass, withHandle = true)

    @Test fun glassInsetsItsCarrierWithoutHandle() = materialGeometry(UiStyle.LiquidGlass, withHandle = false)

    @Test fun glassDefaultClipsAllFourCarrierCorners() = carrierCorners(explicitMaterialShape = false)

    @Test fun glassRespectsExplicitMaterialShape() = carrierCorners(explicitMaterialShape = true)

    @Test fun glassFloatingMarginsDismissWhileCarrierRemainsInteractive() {
        val open = mutableStateOf(true)
        var dismissals = 0
        var contentClicks = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(Modifier.fillMaxSize(), background = Color.White) {
                        if (open.value) {
                            GlassModalBottomSheet(
                                onDismissRequest = { dismissals++; open.value = false },
                                sheetState = rememberBottomSheetState(
                                    initialValue = SheetValue.Hidden,
                                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                                ),
                                dragHandle = null,
                                contentWindowInsets = { WindowInsets(0) },
                            ) {
                                Box(Modifier.fillMaxWidth().height(300.dp)
                                    .clickable { contentClicks++ }.testTag("touch-content"))
                            }
                        }
                    }
                }
            }
        }
        repeat(3) { margin ->
            val bounds = compose.onNodeWithTag("touch-content").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("touch-content").performTouchInput {
                click(Offset(2f, height / 2f))
            }
            compose.runOnIdle { assertEquals(margin + 1, contentClicks) }
            val outside = when (margin) {
                0 -> Offset(bounds.left / 2f, bounds.center.y)
                1 -> Offset(bounds.right + 4f, bounds.center.y)
                else -> Offset(bounds.center.x, bounds.bottom + 4f)
            }
            compose.onNode(isDialog()).performTouchInput { click(outside) }
            compose.onNodeWithTag("touch-content").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals("floating margin is outside the sheet's touch surface", margin + 1, dismissals)
                if (margin < 2) open.value = true
            }
        }
    }

    private fun materialGeometry(style: UiStyle, withHandle: Boolean) {
        val useGlassWrapper = mutableStateOf(false)
        var materialScope: GlassMaterialScope? = null
        val handle: (@Composable () -> Unit)? = if (withHandle) {
            { Box(Modifier.size(80.dp, 48.dp).background(Color.Green).testTag("custom-handle")) }
        } else null
        val body: @Composable ColumnScope.() -> Unit = {
            materialScope = LocalGlassMaterialBackdrop.current as? GlassMaterialScope
            Box(Modifier.fillMaxWidth().height(300.dp).background(Color.Magenta).testTag("geometry-content"))
        }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost(Modifier.fillMaxSize()) {
                        if (useGlassWrapper.value) {
                            GlassModalBottomSheet(
                                onDismissRequest = {}, dragHandle = handle,
                                sheetState = rememberBottomSheetState(
                                    initialValue = SheetValue.Hidden,
                                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                                ),
                                contentWindowInsets = { WindowInsets(bottom = 80) }, content = body,
                            )
                        } else {
                            MaterialBottomSheet(
                                onDismissRequest = {}, dragHandle = handle,
                                sheetState = rememberBottomSheetState(
                                    initialValue = SheetValue.Hidden,
                                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                                ),
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
        val inset = if (style == UiStyle.LiquidGlass) 8f else 0f
        val expected = Rect(materialBounds.left + inset, materialBounds.top - inset,
            materialBounds.right - inset, materialBounds.bottom - inset)
        assertEquals("only glass's outer margins change Material's intrinsic geometry", expected,
            compose.onNodeWithTag("geometry-content").fetchSemanticsNode().boundsInRoot)
        if (style == UiStyle.LiquidGlass) {
            compose.runOnIdle {
                val coordinates = requireNotNull(materialScope?.coordinates)
                val pane = coordinates.boundsInRoot()
                assertEquals("exported material starts at the actual inset carrier", expected.left, pane.left, 1f)
                assertEquals(expected.right, pane.right, 1f)
                assertEquals("bottom inset remains inside the optical carrier", expected.bottom + 80f, pane.bottom, 1f)
                assertEquals("drag handle belongs to the same carrier", expected.top - (if (withHandle) 48f else 0f), pane.top, 1f)
            }
        }
        if (withHandle) {
            assertEquals(materialHandle!!.translate(Offset(0f, -inset)),
                compose.onNodeWithTag("custom-handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
            val image = compose.onNodeWithTag("custom-handle", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            assertEquals("native drag handle must be drawn too", android.graphics.Color.GREEN,
                image.getPixel(image.width / 2, image.height / 2))
        }
    }

    private fun carrierCorners(explicitMaterialShape: Boolean) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(Modifier.fillMaxSize(), background = Color.White) {
                        val requestedShape: Shape? = if (explicitMaterialShape) BottomSheetDefaults.ExpandedShape else null
                        GlassModalBottomSheet(
                            onDismissRequest = {},
                            sheetState = rememberBottomSheetState(
                                initialValue = SheetValue.Hidden,
                                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                            ),
                            shape = requestedShape,
                            dragHandle = null,
                            contentWindowInsets = { WindowInsets(0) },
                        ) {
                            Box(Modifier.fillMaxWidth().height(300.dp).background(Color.Magenta).testTag("corner-content"))
                        }
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("corner-content").fetchSemanticsNode().boundsInRoot
        val image = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val left = bounds.left.toInt() + 1
        val right = bounds.right.toInt() - 2
        val top = bounds.top.toInt() + 1
        val bottom = bounds.bottom.toInt() - 2
        assertTrue("top left is clipped", image.getPixel(left, top) != android.graphics.Color.MAGENTA)
        assertTrue("top right is clipped", image.getPixel(right, top) != android.graphics.Color.MAGENTA)
        for (x in listOf(left, right)) {
            assertEquals("explicit top-only shape keeps its square bottom; glass default rounds it",
                explicitMaterialShape, image.getPixel(x, bottom) == android.graphics.Color.MAGENTA)
        }
        assertEquals(android.graphics.Color.MAGENTA, image.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt()))
    }

    private fun halfHeightSheet(style: UiStyle) {
        val open = mutableStateOf(true)
        val menuOpen = mutableStateOf(false)
        var sheetScene: GlassHostState? = null
        var inheritedSampling: GlassHostState? = null
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
                                inheritedSampling = LocalGlassOverlaySamplingHost.current
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
                if (style == UiStyle.LiquidGlass) {
                    assertSame("nested glass menu samples the sheet, not the route", sheetScene, menuSource)
                } else {
                    assertSame("Material menu preserves its inherited context without adding an optical scope",
                        inheritedSampling, menuSource)
                }
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
