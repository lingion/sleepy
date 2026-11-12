package com.lingion.sleepy.ui.component.liquidglass

import android.view.Window
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35])
class GlassModalScrimTest {
    @get:Rule val compose = createComposeRule()

    @Test fun leavingNativeLightAppearanceRestoresTheExistingDialogDim() {
        val nativeLight = mutableStateOf(false)
        lateinit var window: Window
        compose.setContent {
            Dialog(onDismissRequest = {}) {
                window = (LocalView.current.parent as DialogWindowProvider).window
                GlassDialogScrim(nativeLight.value)
                Box(Modifier.size(80.dp))
            }
        }
        var original = 0f
        compose.runOnIdle { original = window.attributes.dimAmount; nativeLight.value = true }
        compose.runOnIdle { assertEquals(0.2f, window.attributes.dimAmount, 0f); nativeLight.value = false }
        compose.runOnIdle { assertEquals(original, window.attributes.dimAmount, 0f) }
    }

    @Test fun disposingTheDialogRestoresItsOriginalWindowDim() {
        val shown = mutableStateOf(true)
        val nativeLight = mutableStateOf(false)
        lateinit var window: Window
        compose.setContent {
            if (shown.value) {
                Dialog(onDismissRequest = {}) {
                    window = (LocalView.current.parent as DialogWindowProvider).window
                    GlassDialogScrim(nativeLight.value)
                    Box(Modifier.size(80.dp))
                }
            }
        }
        var original = 0f
        compose.runOnIdle { original = window.attributes.dimAmount; nativeLight.value = true }
        compose.runOnIdle { assertEquals(0.2f, window.attributes.dimAmount, 0f); shown.value = false }
        compose.runOnIdle { assertEquals(original, window.attributes.dimAmount, 0f) }
    }

    @Test fun onlyAnUnspecifiedLightGlassSheetScrimUsesTheNativeFit() {
        val material = Color.Black.copy(alpha = 0.32f)
        assertEquals(Color.Black.copy(alpha = 0.2f), glassModalScrimColor(Color.Unspecified, true, false, material))
        assertEquals(material, glassModalScrimColor(Color.Unspecified, false, false, material))
        assertEquals(material, glassModalScrimColor(Color.Unspecified, true, true, material))
        // Explicit caller values, including transparent and the Material default itself, win.
        for (requested in listOf(Color.Transparent, material, Color.Red.copy(alpha = 0.6f))) {
            assertEquals(requested, glassModalScrimColor(requested, true, false, material))
        }
    }
}
