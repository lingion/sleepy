package com.lingion.sleepy.ui.component.liquidglass

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.ui.component.PillNavItemSpec
import com.lingion.sleepy.ui.component.PillNavigationBar
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.theme.LocalDarkTheme
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.ThemePresets
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.ui.theme.WakeUpColorScheme
import com.lingion.sleepy.util.AppPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compare rendered controls against their Classic implementations, not a second glass palette. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class SelectionThemeParityTest {
    @get:Rule val compose = createComposeRule()

    private data class ThemeCase(val name: String, val dark: Boolean, val scheme: ColorScheme)
    private val themes = ThemePresets.all.flatMap { preset ->
        listOf(
            ThemeCase("${preset.key}-light", false, preset.light.material(false)),
            ThemeCase("${preset.key}-dark", true, preset.dark.material(true)),
        )
    }
    private val theme = mutableStateOf(themes.first())

    @Test fun selectedSegmentsAndChipsReuseClassicFillAndForegroundAcrossThemes() {
        page {
            for (style in listOf(UiStyle.Classic, UiStyle.LiquidGlass)) {
                styled(style) {
                    SegmentedSwitcher(
                        options = listOf(0 to "Selected", 1 to "Other"),
                        selected = 0, onSelect = {},
                        modifier = Modifier.fillMaxWidth().testTag("segments-$style"),
                    )
                    for (enabled in listOf(true, false)) {
                        GlassFilterChip(
                            selected = true, enabled = enabled, onClick = {},
                            label = { Text("Selected") },
                            // Opaque disabled overrides prove the original disabled tokens
                            // reach Material unchanged, independent of the parent's surface.
                            colors = FilterChipDefaults.filterChipColors().let { defaults ->
                                if (enabled) defaults else defaults.copy(
                                    disabledSelectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingIcon = {
                                Box(Modifier.size(4.dp).background(LocalContentColor.current)
                                    .testTag("leading-$style-$enabled"))
                            },
                            trailingIcon = {
                                Box(Modifier.size(4.dp).background(LocalContentColor.current)
                                    .testTag("trailing-$style-$enabled"))
                            },
                            modifier = Modifier.testTag("chip-$style-$enabled"),
                        )
                    }
                }
            }
        }
        themes.forEach { case ->
            compose.runOnIdle { theme.value = case }
            val classic = image("segments-${UiStyle.Classic}")
            val glass = image("segments-${UiStyle.LiquidGlass}")
            assertEquals("${case.name}: selected segment fill", classic.getPixel(12, classic.height / 2),
                glass.getPixel(12, glass.height / 2))
            assertEquals("${case.name}: selected segment foreground", textColors("segments-${UiStyle.Classic}"),
                textColors("segments-${UiStyle.LiquidGlass}"))
            for (enabled in listOf(true, false)) {
                val classicChip = image("chip-${UiStyle.Classic}-$enabled")
                val glassChip = image("chip-${UiStyle.LiquidGlass}-$enabled")
                // Sample above the glyphs, inside the chip's painted selected container.
                assertEquals("${case.name}: selected chip fill (enabled=$enabled)",
                    classicChip.getPixel(classicChip.width / 2, classicChip.height / 2 - 12),
                    glassChip.getPixel(glassChip.width / 2, glassChip.height / 2 - 12))
                assertEquals("${case.name}: selected chip foreground (enabled=$enabled)",
                    textColors("chip-${UiStyle.Classic}-$enabled"), textColors("chip-${UiStyle.LiquidGlass}-$enabled"))
                for (icon in listOf("leading", "trailing")) {
                    val classicIcon = image("$icon-${UiStyle.Classic}-$enabled")
                    val glassIcon = image("$icon-${UiStyle.LiquidGlass}-$enabled")
                    assertEquals("${case.name}: chip $icon icon (enabled=$enabled)",
                        classicIcon.getPixel(classicIcon.width / 2, classicIcon.height / 2),
                        glassIcon.getPixel(glassIcon.width / 2, glassIcon.height / 2))
                }
            }
        }
    }

    @Test fun switchesReuseClassicTrackThumbAndIconColorsIncludingDisabledStates() {
        page {
            for (style in listOf(UiStyle.Classic, UiStyle.LiquidGlass)) {
                styled(style) {
                    for (enabled in listOf(true, false)) {
                        Row {
                            for (checked in listOf(true, false)) {
                                GlassSwitch(
                                    checked = checked, enabled = enabled, onCheckedChange = {},
                                    modifier = Modifier.testTag("switch-$style-$enabled-$checked"),
                                    thumbContent = {
                                        Box(Modifier.size(4.dp).background(LocalContentColor.current)
                                            .testTag("icon-$style-$enabled-$checked"))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        themes.forEach { case ->
            compose.runOnIdle { theme.value = case }
            for (enabled in listOf(true, false)) {
                for (checked in listOf(true, false)) {
                    val classic = image("switch-${UiStyle.Classic}-$enabled-$checked")
                    val glass = image("switch-${UiStyle.LiquidGlass}-$enabled-$checked")
                    // Sample each shape's interior, away from its distinct edge geometry.
                    val classicThumbX = if (checked) classic.width - 16 else 16
                    val glassThumbX = glass.width / 2 + if (checked) 11 else -11
                    assertEquals("${case.name}: switch track (enabled=$enabled, checked=$checked)",
                        classic.getPixel(if (checked) 8 else classic.width - 8, classic.height / 2),
                        glass.getPixel(if (checked) 8 else glass.width - 8, glass.height / 2))
                    assertEquals("${case.name}: switch thumb (enabled=$enabled, checked=$checked)",
                        classic.getPixel(classicThumbX, classic.height / 2 - 6),
                        glass.getPixel(glassThumbX, glass.height / 2 - 6))
                    val classicIcon = image("icon-${UiStyle.Classic}-$enabled-$checked")
                    val glassIcon = image("icon-${UiStyle.LiquidGlass}-$enabled-$checked")
                    assertEquals("${case.name}: switch icon (enabled=$enabled, checked=$checked)",
                        classicIcon.getPixel(classicIcon.width / 2, classicIcon.height / 2),
                        glassIcon.getPixel(glassIcon.width / 2, glassIcon.height / 2))
                }
            }
        }
    }

    @Test fun glassDockReusesClassicSelectedFillTextIconAndBadgeColors() {
        val items = listOf(
            PillNavItemSpec(Icons.Filled.Home, "Selected", badge = true),
            PillNavItemSpec(Icons.Filled.Check, "Other"),
        )
        page {
            for ((name, dockTheme) in listOf("classic" to AppPrefs.DOCK_THEME_CLASSIC, "glass" to AppPrefs.DOCK_THEME_LIQUID_GLASS)) {
                styled(if (dockTheme == AppPrefs.DOCK_THEME_LIQUID_GLASS) UiStyle.LiquidGlass else UiStyle.Classic) {
                    PillNavigationBar(
                        items = items, selectedIndex = 0, onSelect = {}, dock = true, dockTheme = dockTheme,
                        modifier = Modifier.testTag("dock-$name"),
                    )
                }
            }
        }
        themes.forEach { case ->
            compose.runOnIdle { theme.value = case }
            // This node exists only in the optical implementation: comparing two
            // classic Dock instances would otherwise make all color checks pass.
            compose.onNodeWithTag("dock-lens").assertExists()
            val classic = image("dock-classic")
            val glass = image("dock-glass")
            for (color in listOf(case.scheme.secondaryContainer, case.scheme.onSecondaryContainer, case.scheme.primary)) {
                assertTrue("${case.name}: Classic paints the reference color $color", color.toArgb() in solidColors(classic))
                assertTrue("${case.name}: glass reuses Classic dock color $color", color.toArgb() in solidColors(glass))
            }
            assertTrue("${case.name}: glass retains the exact Classic selected text token",
                textColors("dock-glass").containsAll(textColors("dock-classic")))
        }
    }

    @Test fun materialDockIgnoresSavedGlassPreferenceAndRestoresItOnlyInGlassMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previousPreference = AppPrefs.getDockTheme(context)
        val style = mutableStateOf(UiStyle.Classic)
        val selected = mutableStateOf(0)
        val callbacks = mutableListOf<Int>()
        val items = listOf(
            PillNavItemSpec(Icons.Filled.Home, "Selected"),
            PillNavItemSpec(Icons.Filled.Check, "Other"),
        )
        try {
            AppPrefs.setDockTheme(context, AppPrefs.DOCK_THEME_LIQUID_GLASS)
            page {
                styled(style.value) {
                    PillNavigationBar(
                        items = items, selectedIndex = selected.value,
                        onSelect = { callbacks += it; selected.value = it }, dock = true,
                        modifier = Modifier.testTag("mode-isolated-dock"),
                    )
                }
            }
            compose.onNodeWithTag("dock-lens").assertDoesNotExist()
            compose.onNodeWithText("Other").performClick()
            compose.runOnIdle {
                assertEquals(listOf(1), callbacks)
                assertEquals(1, selected.value)
                assertEquals(AppPrefs.DOCK_THEME_LIQUID_GLASS, AppPrefs.getDockTheme(context))
                style.value = UiStyle.LiquidGlass
            }
            compose.onNodeWithTag("dock-lens").assertExists()
            compose.runOnIdle { style.value = UiStyle.Classic }
            compose.onNodeWithTag("dock-lens").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals("mode changes preserve the controlled destination", 1, selected.value)
                assertEquals("mode changes must not navigate", listOf(1), callbacks)
                assertEquals("Material mode preserves the user's glass preference for later",
                    AppPrefs.DOCK_THEME_LIQUID_GLASS, AppPrefs.getDockTheme(context))
            }
        } finally {
            AppPrefs.setDockTheme(context, previousPreference)
        }
    }

    private fun page(content: @Composable () -> Unit) {
        compose.setContent {
            val case = theme.value
            key(case.name) {
                MaterialTheme(colorScheme = case.scheme) {
                    CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalDarkTheme provides case.dark) {
                        GlassHost(Modifier.fillMaxSize(), background = case.scheme.background) {
                            Column(Modifier.fillMaxSize().background(case.scheme.background)) { content() }
                        }
                    }
                }
            }
        }
    }

    @Composable private fun styled(style: UiStyle, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDockInputTimeNanos provides { compose.mainClock.currentTime * 1_000_000L }, LocalUiStyle provides style, content = content)
    }

    private fun image(tag: String): Bitmap = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()

    private fun textColors(tag: String): List<Color> = compose.onAllNodes(
        hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().flatMap { node ->
        mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action!!(it) }
    }.map { it.layoutInput.style.color }.also { assertTrue("$tag exposes rendered text", it.isNotEmpty()) }

    /** Ignore antialiased edges; retain only colors with a genuinely solid 2x2 area. */
    private fun solidColors(bitmap: Bitmap): Set<Int> = buildSet {
        for (y in 0 until bitmap.height - 1) for (x in 0 until bitmap.width - 1) {
            val color = bitmap.getPixel(x, y)
            if (bitmap.getPixel(x + 1, y) == color && bitmap.getPixel(x, y + 1) == color && bitmap.getPixel(x + 1, y + 1) == color) add(color)
        }
    }

    private fun WakeUpColorScheme.material(dark: Boolean): ColorScheme {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary, onPrimary = onPrimary, secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer, onSurface = onSurface, onSurfaceVariant = onSurfaceVariant,
            surface = surface, background = background, surfaceContainer = surfaceContainer,
            surfaceContainerHighest = surfaceContainerHighest, outline = outline, outlineVariant = outlineVariant,
        )
    }
}
