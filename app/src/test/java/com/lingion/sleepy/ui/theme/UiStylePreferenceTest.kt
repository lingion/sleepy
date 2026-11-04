package com.lingion.sleepy.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35])
class UiStylePreferenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("sleepy_prefs", Context.MODE_PRIVATE)
    @Before fun clearTestPreferences() { prefs.edit().clear().commit() }

    @Test fun firstInstallIsClassicWithoutMutatingPreferencesOnRead() {
        assertEquals(UiStyle.Classic, AppPrefs.getUiStyle(context))
        assertFalse(prefs.contains(AppPrefs.KEY_UI_STYLE))
    }
    @Test fun oldDockSelectionIsInheritedWithoutChangingDockShape() {
        prefs.edit().putString(AppPrefs.KEY_DOCK_THEME, "liquid_glass").putBoolean(AppPrefs.KEY_NAV_DOCK, false).commit()
        assertEquals(UiStyle.LiquidGlass, AppPrefs.getUiStyle(context))
        assertFalse(AppPrefs.isNavDock(context))
    }
    @Test fun newKeyWinsOverOldAndDoesNotOverwriteColorTheme() {
        prefs.edit().putString(AppPrefs.KEY_DOCK_THEME, "liquid_glass").putString(AppPrefs.KEY_THEME_MODE, "dark").commit()
        AppPrefs.setUiStyle(context, UiStyle.Classic)
        assertEquals(UiStyle.Classic, AppPrefs.getUiStyle(context))
        assertEquals("dark", prefs.getString(AppPrefs.KEY_THEME_MODE, ""))
        assertEquals("liquid_glass", prefs.getString(AppPrefs.KEY_DOCK_THEME, ""))
    }
    @Test fun invalidNewValueFallsBackWithoutResurrectingOldGlass() {
        prefs.edit().putString(AppPrefs.KEY_DOCK_THEME, "liquid_glass").putString(AppPrefs.KEY_UI_STYLE, "broken").commit()
        assertEquals(UiStyle.Classic, AppPrefs.getUiStyle(context))
    }
    @Test fun legacyAccessorsAndInitialFlowUseTheNewSingleSource() = runBlocking {
        AppPrefs.setDockTheme(context, AppPrefs.DOCK_THEME_LIQUID_GLASS)
        assertEquals("liquid_glass", prefs.getString(AppPrefs.KEY_UI_STYLE, ""))
        assertEquals(UiStyle.LiquidGlass, AppPrefs.uiStyleFlow(context).first())
        assertEquals("liquid_glass", AppPrefs.dockThemeFlow(context).first())
        AppPrefs.setUiStyle(context, UiStyle.Classic)
        assertEquals("classic", AppPrefs.getDockTheme(context))
    }
}
