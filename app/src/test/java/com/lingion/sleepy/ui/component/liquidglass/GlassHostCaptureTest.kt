package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Explicit source declarations preserve the original pixel expectations; no assertion is relaxed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31, 35], qualifiers = "w240dp-h240dp-mdpi")
class GlassHostCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overlaySceneIncludesOrdinaryPageContentNotRegisteredAsInlineBackground() {
        lateinit var state: GlassHostState
        compose.setContent {
            state = rememberGlassHostState()
            GlassHost(state = state, background = Color.Green, modifier = Modifier.size(120.dp)) {
                Box(Modifier.size(120.dp).background(Color.Red))
            }
        }
        compose.onRoot().captureToImage()
        val inline = runBlocking { state.sourceLayer.toImageBitmap() }.asAndroidBitmap()
        val overlay = runBlocking { state.sceneLayer.toImageBitmap() }.asAndroidBitmap()
        assertEquals("undeclared ordinary drawing is not guessed into inline prefixes", android.graphics.Color.GREEN, inline.getPixel(30, 30))
        assertEquals("a separate popup samples the already completed page", android.graphics.Color.RED, overlay.getPixel(30, 30))
    }

    @Test fun matchingMaterialsShareOneFilterResourceWhileOtherSourcesStaySeparate() {
        lateinit var state: GlassHostState
        compose.setContent { state = rememberGlassHostState(); GlassHost(state = state) {} }
        compose.runOnIdle {
            val first = state.filterBand(1f, false, 2.5f)
            org.junit.Assert.assertNotNull(first)
            org.junit.Assert.assertSame(first, state.filterBand(1f, false, 2.5f))
            org.junit.Assert.assertNotSame(first, state.filterBand(1f, true, 2.5f))
            assertEquals("a different prefix cannot accidentally reuse the default prefix", null, state.filterBand(3f, false, 2.5f))
        }
    }

    @Test fun firstLateFilterRequestWakesAnOtherwiseStaticHost() {
        lateinit var state: GlassHostState
        compose.setContent {
            state = rememberGlassHostState()
            GlassHost(state = state, modifier = Modifier.size(120.dp)) {
                Box(Modifier.size(120.dp).background(Color.Red))
            }
        }
        compose.onRoot().captureToImage()
        var band: GlassFilterBand? = null
        compose.runOnIdle { band = state.filterBand(1f, true, 16f) }
        compose.onRoot().captureToImage()
        compose.runOnIdle { org.junit.Assert.assertTrue("a popup's first request cannot remain permanently on the raw source", band?.ready == true) }
    }

    @Test fun sourceExcludesPaneForegroundWhileDisplayRetainsIt() {
        lateinit var state: GlassHostState
        compose.setContent {
            state = rememberGlassHostState()
            GlassHost(state = state, modifier = Modifier.size(120.dp)) {
                Box(Modifier.size(120.dp)) {
                    GlassBackground(depth = 0f, modifier = Modifier.size(120.dp)) {
                        Box(Modifier.size(120.dp).background(Color.Red))
                    }
                    GlassSurface(Modifier.offset(20.dp, 20.dp).size(40.dp)) {
                        Box(Modifier.size(40.dp).background(Color.Blue))
                    }
                }
            }
        }
        compose.waitForIdle()
        val display = compose.onRoot().captureToImage().asAndroidBitmap()
        val source = runBlocking { state.sourceLayer.toImageBitmap() }.asAndroidBitmap()
        assertEquals("display includes the original foreground", android.graphics.Color.BLUE, display.getPixel(30, 30))
        assertEquals("capture excludes every glass consumer and its foreground", android.graphics.Color.RED, source.getPixel(30, 30))
    }

    @Test fun paneSourceDoesNotIncludeLaterOrdinaryDrawing() {
        lateinit var state: GlassHostState
        compose.setContent {
            state = rememberGlassHostState()
            GlassHost(state = state, modifier = Modifier.size(120.dp)) {
                Box(Modifier.size(120.dp)) {
                    GlassBackground(depth = 0f, modifier = Modifier.size(120.dp)) {
                        Box(Modifier.size(120.dp).background(Color.Green))
                    }
                    GlassSurface(Modifier.size(80.dp)) {
                        Box(Modifier.size(80.dp).background(Color.Blue))
                    }
                    GlassBackground(depth = 2f, modifier = Modifier.size(80.dp)) {
                        Box(Modifier.size(80.dp).background(Color.Red.copy(alpha = 0.5f)))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        val source = runBlocking { state.sourceLayer.toImageBitmap() }.asAndroidBitmap()
        assertEquals("later translucent content must not be refracted as earlier background", android.graphics.Color.GREEN, source.getPixel(30, 30))
    }

    @Test fun ordinaryLaterSiblingOccludesThePane() {
        compose.setContent {
            GlassHost(modifier = Modifier.size(120.dp)) {
                Box(Modifier.size(120.dp)) {
                    GlassBackground(depth = 0f, modifier = Modifier.size(120.dp)) {
                        Box(Modifier.size(120.dp).background(Color.Green))
                    }
                    GlassSurface(Modifier.size(80.dp)) {
                        Box(Modifier.size(80.dp).background(Color.Blue))
                    }
                    Box(Modifier.size(80.dp).background(Color.Red))
                }
            }
        }
        compose.waitForIdle()
        val display = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals("glass must not be lifted above later opaque content", android.graphics.Color.RED, display.getPixel(30, 30))
    }

    @Test fun addingAndRemovingAnEqualDepthBackgroundDoesNotDependOnRegistrationOrder() {
        val visible = mutableStateOf(false)
        compose.setContent {
            GlassHost(modifier = Modifier.size(120.dp)) {
                Box(Modifier.size(40.dp).glassBackdropSource(0f).background(Color.Red))
                if (visible.value) {
                    GlassBackground(depth = 0f, modifier = Modifier.offset(60.dp, 0.dp).size(40.dp)) {
                        Box(Modifier.size(40.dp).background(Color.Green))
                    }
                }
            }
        }
        compose.onRoot().captureToImage()
        compose.runOnIdle { visible.value = true }
        compose.onRoot().captureToImage()
        compose.runOnIdle { visible.value = false }
        compose.onRoot().captureToImage()
    }

    @Test fun retainedPaneContentAndBackdropUpdateWithoutSelfFeedback() {
        lateinit var state: GlassHostState
        val background = mutableStateOf(Color.Red)
        val foreground = mutableStateOf(Color.Blue)
        compose.setContent {
            state = rememberGlassHostState()
            GlassHost(state = state, modifier = Modifier.size(120.dp)) {
                Box(Modifier.size(120.dp)) {
                    GlassBackground(depth = 0f, modifier = Modifier.size(120.dp)) {
                        Box(Modifier.size(120.dp).background(background.value))
                    }
                    GlassSurface(Modifier.offset(20.dp, 20.dp).size(40.dp)) {
                        Box(Modifier.size(40.dp).background(foreground.value))
                    }
                    GlassSurface(Modifier.offset(40.dp, 40.dp).size(40.dp)) {
                        Box(Modifier.size(40.dp).background(Color.Yellow))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { background.value = Color.Green; foreground.value = Color.Magenta }
        compose.waitForIdle()
        val display = compose.onRoot().captureToImage().asAndroidBitmap()
        val source = runBlocking { state.sourceLayer.toImageBitmap() }.asAndroidBitmap()
        assertEquals(android.graphics.Color.MAGENTA, display.getPixel(30, 30))
        assertEquals("later pane retains paint order", android.graphics.Color.YELLOW, display.getPixel(50, 50))
        assertEquals(android.graphics.Color.GREEN, source.getPixel(30, 30))
        assertEquals(android.graphics.Color.GREEN, source.getPixel(50, 50))
    }
}
