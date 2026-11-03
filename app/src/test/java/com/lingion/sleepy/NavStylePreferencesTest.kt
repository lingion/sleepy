package com.lingion.sleepy

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import com.lingion.sleepy.util.AppPrefs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NavStylePreferencesTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    private val styles = listOf(
        AppPrefs.NAV_STYLE_DOCKED,
        AppPrefs.NAV_STYLE_CLASSIC,
        AppPrefs.NAV_STYLE_LIQUID_GLASS,
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("sleepy_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun new_install_defaults_to_docked() {
        assertEquals(AppPrefs.NAV_STYLE_DOCKED, AppPrefs.getNavStyle(context))
    }

    @Test
    fun legacy_combinations_preserve_the_previously_visible_style() {
        for (dock in listOf(false, true)) {
            for (theme in listOf(null, "classic", "liquid_glass", "unknown")) {
                prefs.edit().clear()
                    .putBoolean("nav_dock", dock)
                    .putString("dock_theme", theme)
                    .commit()
                val expected = when {
                    !dock -> AppPrefs.NAV_STYLE_DOCKED
                    theme == "liquid_glass" -> AppPrefs.NAV_STYLE_LIQUID_GLASS
                    else -> AppPrefs.NAV_STYLE_CLASSIC
                }
                assertEquals("nav_dock=$dock, dock_theme=$theme", expected, AppPrefs.getNavStyle(context))
            }
        }
    }

    @Test
    fun unified_style_takes_precedence_over_conflicting_legacy_settings() {
        for (style in styles) {
            prefs.edit()
                .putString(AppPrefs.KEY_NAV_STYLE, style)
                .putBoolean("nav_dock", style == AppPrefs.NAV_STYLE_DOCKED)
                .putString("dock_theme", "liquid_glass")
                .commit()
            assertEquals(style, AppPrefs.getNavStyle(context))
        }
    }

    @Test
    fun each_style_can_be_selected_directly_from_every_other_style() {
        for (from in styles) {
            for (to in styles) {
                AppPrefs.setNavStyle(context, from)
                AppPrefs.setNavStyle(context, to)
                assertEquals(to, prefs.getString(AppPrefs.KEY_NAV_STYLE, null))
                assertEquals(to, AppPrefs.getNavStyle(context))
            }
        }
    }

    @Test
    fun first_selection_replaces_both_legacy_settings() {
        prefs.edit().putBoolean("nav_dock", false).putString("dock_theme", "classic").commit()

        AppPrefs.setNavStyle(context, AppPrefs.NAV_STYLE_LIQUID_GLASS)

        assertEquals(AppPrefs.NAV_STYLE_LIQUID_GLASS, AppPrefs.getNavStyle(context))
        assertFalse(prefs.contains("nav_dock"))
        assertFalse(prefs.contains("dock_theme"))
    }

    @Test
    fun invalid_selection_is_rejected_without_changing_the_saved_style() {
        AppPrefs.setNavStyle(context, AppPrefs.NAV_STYLE_CLASSIC)

        assertThrows(IllegalArgumentException::class.java) {
            AppPrefs.setNavStyle(context, "unknown")
        }

        assertEquals(AppPrefs.NAV_STYLE_CLASSIC, AppPrefs.getNavStyle(context))
    }

    @Test
    fun unknown_saved_style_falls_back_to_a_valid_legacy_style() {
        prefs.edit().putString(AppPrefs.KEY_NAV_STYLE, "unknown")
            .putBoolean("nav_dock", true).putString("dock_theme", "liquid_glass").commit()

        assertEquals(AppPrefs.NAV_STYLE_LIQUID_GLASS, AppPrefs.getNavStyle(context))
    }

    @Test
    fun active_subscriber_observes_all_three_styles_without_restart() = runBlocking {
        val updates = Channel<String>(Channel.UNLIMITED)
        val subscription = launch(start = CoroutineStart.UNDISPATCHED) {
            AppPrefs.navStyleFlow(context).collect { updates.send(it) }
        }
        try {
            assertEquals(AppPrefs.NAV_STYLE_DOCKED, withTimeout(5_000) { updates.receive() })
            for (style in listOf(AppPrefs.NAV_STYLE_LIQUID_GLASS, AppPrefs.NAV_STYLE_CLASSIC, AppPrefs.NAV_STYLE_DOCKED)) {
                AppPrefs.setNavStyle(context, style)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(style, withTimeout(5_000) { updates.receive() })
            }
        } finally {
            subscription.cancelAndJoin()
            updates.close()
        }
    }
}
