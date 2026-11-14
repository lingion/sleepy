package com.lingion.sleepy.ui.screen.mine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.ui.component.liquidglass.GlassHost
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h800dp-mdpi")
class AboutPrivacyCardTest {
    @get:Rule val compose = createComposeRule()
    private var opens = 0

    @Test fun materialPrivacyLinkAndAccessibleIconOpenTheSameDestination() =
        assertLinkCard(UiStyle.Classic)

    @Test fun glassPrivacyLinkKeepsTextAlignmentAndAccessibleTouchTarget() =
        assertGlassLinkCard(328.dp, shouldWrap = false)

    @Test fun glassPrivacyLinkWrapsWithinOneButtonOnNarrowCards() =
        assertGlassLinkCard(220.dp, shouldWrap = true)

    @Test fun glassWithoutAHostKeepsTheMaterialLinkAndIconLayout() =
        assertLinkCard(UiStyle.LiquidGlass, hostEnabled = false)

    private fun show(style: UiStyle, width: Dp = 328.dp, hostEnabled: Boolean = style == UiStyle.LiquidGlass) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides style) {
                    GlassHost(enabled = hostEnabled) {
                        Box(Modifier.width(width)) {
                            AboutPrivacyCard(
                                onOpenPrivacy = { opens++ },
                                modifier = Modifier.testTag("privacy_card"),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun assertLinkCard(style: UiStyle, hostEnabled: Boolean = false) {
        show(style, hostEnabled = hostEnabled)
        val title = compose.onNodeWithText("隐私政策")
        val link = compose.onNodeWithText("sleepy.qdp.qzz.io/privacy")
        title.assertIsDisplayed()
        link.assertIsDisplayed()
        assertEquals(
            "The URL keeps the title's leading alignment",
            title.fetchSemanticsNode().boundsInRoot.left,
            link.fetchSemanticsNode().boundsInRoot.left,
            .1f,
        )
        val button = compose.onNodeWithContentDescription("隐私政策")
        button.assertHasClickAction().assertWidthIsAtLeast(40.dp).assertHeightIsAtLeast(40.dp)
        val touchBounds = button.fetchSemanticsNode().touchBoundsInRoot
        val minimumTouchSize = 48f * compose.density.density
        assertTrue("The official icon retains a 48dp touch width", touchBounds.width >= minimumTouchSize)
        assertTrue("The official icon retains a 48dp touch height", touchBounds.height >= minimumTouchSize)
        assertEquals(
            "The external-link action stays vertically centered in its card",
            compose.onNodeWithTag("privacy_card").fetchSemanticsNode().boundsInRoot.center.y,
            button.fetchSemanticsNode().boundsInRoot.center.y,
            .1f,
        )

        val layouts = mutableListOf<TextLayoutResult>()
        link.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        link.performTouchInput { click(layouts.single().getBoundingBox(0).center) }
        assertEquals("The visible URL is an actual link", 1, opens)
        button.performClick()
        assertEquals("The named glass/Material action uses that same link handler", 2, opens)
    }

    private fun assertGlassLinkCard(width: Dp, shouldWrap: Boolean) {
        show(UiStyle.LiquidGlass, width)
        val title = compose.onNodeWithText("隐私政策")
        val button = compose.onNodeWithTag("about_privacy_link")
        val link = compose.onNodeWithText("sleepy.qdp.qzz.io/privacy", useUnmergedTree = true)
        val icon = compose.onNodeWithContentDescription("隐私政策", useUnmergedTree = true)
        title.assertIsDisplayed()
        link.assertIsDisplayed()
        button.assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val titleBounds = title.fetchSemanticsNode().boundsInRoot
        val buttonBounds = button.fetchSemanticsNode().boundsInRoot
        val linkBounds = link.fetchSemanticsNode().boundsInRoot
        val iconBounds = icon.fetchSemanticsNode().boundsInRoot
        assertEquals("The title and glass button share their leading edge", titleBounds.left, buttonBounds.left, .1f)
        assertEquals("The title and glass button share their trailing edge", titleBounds.right, buttonBounds.right, .1f)
        assertEquals("The URL has a 12dp leading inset", 12f, linkBounds.left - buttonBounds.left, .1f)
        assertEquals("The external icon has a 12dp trailing inset", 12f, buttonBounds.right - iconBounds.right, .1f)
        assertEquals("The URL and external icon keep an 8dp gap", 8f, iconBounds.left - linkBounds.right, .1f)
        assertTrue("The button remains below the title", buttonBounds.top >= titleBounds.bottom)
        val cardBounds = compose.onNodeWithTag("privacy_card").fetchSemanticsNode().boundsInRoot
        assertEquals("The link preserves the card's trailing inset", 20f, cardBounds.right - buttonBounds.right, .1f)

        val layouts = mutableListOf<TextLayoutResult>()
        link.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals("The URL wraps only when the card needs it", shouldWrap, layout.lineCount > 1)
        assertFalse("The complete URL fits without clipping", layout.hasVisualOverflow)
        link.performTouchInput { click(layout.getBoundingBox(0).center) }
        assertEquals("Clicking the URL opens once through its glass button", 1, opens)
        button.performClick()
        assertEquals("Clicking the button opens once through the same handler", 2, opens)
        icon.performTouchInput { click(center) }
        assertEquals("The external icon belongs to that same single action", 3, opens)
    }
}
