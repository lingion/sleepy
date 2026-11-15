package com.lingion.sleepy.ui.component.liquidglass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** GPU evidence for the sampling graph; a CPU golden cannot exercise RuntimeShader. */
@RunWith(AndroidJUnit4::class)
class GlassMaterialSamplingRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aChildSamplesItsHalfTransparentCardExactlyOnceAndTracksChanges() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val backing = mutableStateOf(Color.Blue.copy(alpha = .5f))
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(modifier = Modifier.size(120.dp), background = Color.Red) {
                        val scope = rememberGlassMaterialScope(RectangleShape)
                        Box(Modifier.size(100.dp).glassSurfaceBackground(backing.value, RectangleShape, materialScope = scope)) {
                            GlassMaterialContent(scope) {
                                Box(Modifier.offset(20.dp, 20.dp).size(60.dp).glassControl(RectangleShape).testTag("sampling-child"))
                            }
                        }
                    }
                }
            }
        }
        val blue = centerPixel()
        assertTrue("half-blue over red must remain purple without dropping or doubling the backing: ${blue.toUInt().toString(16)}",
            abs(android.graphics.Color.blue(blue) - android.graphics.Color.red(blue)) < 25 &&
                android.graphics.Color.blue(blue) > android.graphics.Color.green(blue) + 20)
        compose.runOnIdle { backing.value = Color.Green.copy(alpha = .5f) }
        val green = centerPixel()
        assertTrue("a retained child must see a changed ordinary backing: ${green.toUInt().toString(16)}",
            abs(android.graphics.Color.green(green) - android.graphics.Color.red(green)) < 25 &&
                android.graphics.Color.green(green) > android.graphics.Color.blue(green) + 20)
    }

    @Test fun twoNestedOpticalBackingsRemainInTheChildsLiveSamplingChain() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val outer = mutableStateOf(Color.Blue)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(modifier = Modifier.size(160.dp), background = Color.Red) {
                        MaterialPane(outer.value, Modifier.size(140.dp)) {
                            MaterialPane(Color.Green, Modifier.offset(20.dp, 20.dp).size(100.dp)) {
                                Box(Modifier.offset(20.dp, 20.dp).size(60.dp).glassControl(RectangleShape).testTag("sampling-child"))
                            }
                        }
                    }
                }
            }
        }
        val first = centerPixel()
        assertTrue("the immediate green optical parent must reach the child: ${first.toUInt().toString(16)}",
            android.graphics.Color.green(first) > android.graphics.Color.blue(first) + 15)
        compose.runOnIdle { outer.value = Color.Magenta }
        val second = centerPixel()
        assertTrue("changing the grandparent must propagate through both optical materials: ${first.toUInt().toString(16)} -> ${second.toUInt().toString(16)}",
            android.graphics.Color.red(second) > android.graphics.Color.red(first) + 4)
    }

    @Test fun exportedMaterialUsesItsOwnRoundedClipOutsideTheParentLayout() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(modifier = Modifier.size(160.dp), background = Color.Red) {
                        val shape = RoundedCornerShape(32.dp)
                        val scope = rememberGlassMaterialScope(shape)
                        Box(Modifier.offset(20.dp, 20.dp).size(100.dp).trackGlassMaterial(scope)
                            .glassControl(shape, tint = Color.Blue, exportedBackdrop = scope?.exported))
                        // A raised lens may extend beyond its backing. Keep the
                        // child as a later sibling so the parent's layout cannot
                        // accidentally hide a missing material-source shape clip.
                        GlassMaterialContent(scope) {
                            Box(Modifier.offset(10.dp, 10.dp).size(60.dp).glassControl(RectangleShape).testTag("sampling-child"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = compose.onNodeWithTag("sampling-child").captureToImage().asAndroidBitmap()
        val outside = image.getPixel(image.width / 5, image.height / 5)
        val inside = image.getPixel(image.width * 3 / 4, image.height * 3 / 4)
        assertTrue("outside the rounded parent source the child must see the red page: ${outside.toUInt().toString(16)}",
            android.graphics.Color.red(outside) > android.graphics.Color.blue(outside) + 20)
        assertTrue("inside the parent's clip the child must see blue material: ${inside.toUInt().toString(16)}",
            android.graphics.Color.blue(inside) > android.graphics.Color.red(inside) + 20)
    }

    @Test fun aChildKeepsItsOwnRoundedClipOverAnOrdinaryCard() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(modifier = Modifier.size(120.dp), background = Color.Red) {
                        val scope = rememberGlassMaterialScope(RectangleShape)
                        Box(Modifier.size(100.dp).glassSurfaceBackground(Color.Blue, RectangleShape, materialScope = scope)) {
                            GlassMaterialContent(scope) {
                                Box(Modifier.offset(20.dp, 20.dp).size(60.dp).glassControl(RoundedCornerShape(20.dp)).testTag("sampling-child"))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = compose.onNodeWithTag("sampling-child").captureToImage().asAndroidBitmap()
        val corner = image.getPixel(0, 0)
        val center = image.getPixel(image.width / 2, image.height / 2)
        assertTrue("outside the child clip its ordinary backing stays blue: ${corner.toUInt().toString(16)}",
            android.graphics.Color.blue(corner) > android.graphics.Color.green(corner) + 80)
        assertTrue("the child's own material remains confined to its rounded shape: ${center.toUInt().toString(16)}",
            android.graphics.Color.green(center) > android.graphics.Color.green(corner) + 30)
    }

    @Test fun anExportedParentMaterialExcludesItsForegroundAndChild() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val foreground = mutableStateOf(Color.Green)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost(modifier = Modifier.size(120.dp), background = Color.Red) {
                        val scope = rememberGlassMaterialScope(RectangleShape)
                        val optical = Modifier.size(100.dp).trackGlassMaterial(scope)
                            .glassControl(RectangleShape, tint = Color.Blue, exportedBackdrop = scope?.exported)
                        Box(optical) {
                            Box(Modifier.size(100.dp).background(foreground.value))
                            GlassMaterialContent(scope) {
                                Box(Modifier.offset(20.dp, 20.dp).size(60.dp).glassControl(RectangleShape).testTag("sampling-child"))
                            }
                        }
                    }
                }
            }
        }
        val first = centerPixel()
        assertTrue("child must sample the blue parent material, not the red page or green foreground: ${first.toUInt().toString(16)}",
            android.graphics.Color.blue(first) > android.graphics.Color.red(first) + 20 &&
                android.graphics.Color.blue(first) > android.graphics.Color.green(first) + 20)
        compose.runOnIdle { foreground.value = Color.Yellow }
        val second = centerPixel()
        assertEquals("changing excluded foreground cannot change the child's optical input", first, second)
    }

    @Test fun materialDesignKeepsItsOrdinaryBackingAndCreatesNoMaterialScope() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                    GlassHost(enabled = false, modifier = Modifier.size(120.dp), background = Color.Red) {
                        val scope = rememberGlassMaterialScope(RectangleShape)
                        check(scope == null)
                        Box(Modifier.size(100.dp).glassSurfaceBackground(Color.Blue, RectangleShape, materialScope = scope)) {
                            GlassMaterialContent(scope) {
                                Box(Modifier.offset(20.dp, 20.dp).size(60.dp).glassControl(RectangleShape).testTag("sampling-child"))
                            }
                        }
                    }
                }
            }
        }
        assertEquals(android.graphics.Color.BLUE, centerPixel())
    }

    private fun centerPixel(): Int {
        compose.waitForIdle()
        val image = compose.onNodeWithTag("sampling-child").captureToImage().asAndroidBitmap()
        return image.getPixel(image.width / 2, image.height / 2)
    }

    @Composable
    private fun MaterialPane(tint: Color, modifier: Modifier, shape: Shape = RectangleShape, content: @Composable () -> Unit) {
        val scope = rememberGlassMaterialScope(shape)
        val optical = modifier.trackGlassMaterial(scope).glassControl(shape, tint = tint, exportedBackdrop = scope?.exported)
        Box(optical) { GlassMaterialContent(scope, content) }
    }
}
