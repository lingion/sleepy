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
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
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
        assertLinkCard(UiStyle.LiquidGlass, width = 328.dp, shouldWrap = false, hostEnabled = true)

    @Test fun glassPrivacyLinkWrapsBesideItsIndependentButtonOnNarrowCards() =
        assertLinkCard(UiStyle.LiquidGlass, width = 220.dp, shouldWrap = true, hostEnabled = true)

    @Test fun glassWithoutAHostKeepsTheMaterialLinkAndIconLayout() =
        assertLinkCard(UiStyle.LiquidGlass, hostEnabled = false)

    @Test fun productionGlassExternalLinksShareTextEdgesAndButtonGeometryWithSeparateDestinations() {
        val opens = intArrayOf(0, 0, 0)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.LiquidGlass) {
                    GlassHost {
                        Box(Modifier.width(328.dp)) {
                            AboutExternalLinks(
                                onOpenSource = { opens[0]++ },
                                onOpenPrivacy = { opens[1]++ },
                                onOpenLicenses = { opens[2]++ },
                                modifier = Modifier.testTag("external_links"),
                            )
                        }
                    }
                }
            }
        }
        val titles = listOf("开源地址", "隐私政策", "开源声明")
        val details = listOf(
            "github.com/lingion/sleepy", "sleepy.qdp.qzz.io/privacy", "在官网查看许可、贡献者与完整来源清单",
        )
        val destinations = listOf(
            "https://github.com/lingion/sleepy", "https://sleepy.qdp.qzz.io/privacy", "https://sleepy.is-my.app/licenses/",
        )
        val actionTags = listOf("about_source_link", "about_privacy_link", "about_license_entry")
        val container = compose.onNodeWithTag("external_links").fetchSemanticsNode().boundsInRoot
        val titleBounds = titles.map { compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        val buttonBounds = actionTags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        details.forEachIndexed { index, detail ->
            val title = compose.onNodeWithText(titles[index], useUnmergedTree = true)
            val link = compose.onNodeWithText(detail, useUnmergedTree = true)
            val button = compose.onNodeWithTag(actionTags[index])
            val icon = compose.onNodeWithContentDescription(titles[index], useUnmergedTree = true)
            title.assertIsDisplayed()
            link.assertIsDisplayed()
            button.assertHasClickAction()
            val linkBounds = link.fetchSemanticsNode().boundsInRoot
            val iconBounds = icon.fetchSemanticsNode().boundsInRoot
            assertEquals("All three titles share a leading edge", titleBounds.first().left, titleBounds[index].left, .1f)
            assertEquals("Each description starts at its title", titleBounds[index].left, linkBounds.left, .1f)
            assertEquals("All three actions share a trailing edge", buttonBounds.first().right, buttonBounds[index].right, .1f)
            assertEquals("All three action widths match", buttonBounds.first().width, buttonBounds[index].width, .1f)
            assertEquals("All three action heights match", buttonBounds.first().height, buttonBounds[index].height, .1f)
            assertEquals("The native action slot is 48dp wide", 48f * compose.density.density, buttonBounds[index].width, .1f)
            assertEquals("The native action slot is 48dp high", 48f * compose.density.density, buttonBounds[index].height, .1f)
            assertEquals("Each external icon is 20dp wide", 20f * compose.density.density, iconBounds.width, .1f)
            assertEquals("Each external icon is 20dp high", 20f * compose.density.density, iconBounds.height, .1f)
            assertEquals("Each icon is centered in its slot", buttonBounds[index].center.x, iconBounds.center.x, .1f)
            assertTrue("Descriptions leave room for their independent action", linkBounds.right <= buttonBounds[index].left + .1f)
            assertTrue("Titles fit inside the production container", titleBounds[index].left >= container.left && titleBounds[index].right <= container.right)
            assertTrue("Descriptions fit inside the production container", linkBounds.left >= container.left && linkBounds.right <= container.right)
            assertTrue("Actions fit inside the production container", buttonBounds[index].right <= container.right)
            val layout = assertActualLink(link, destinations[index])
            assertFalse("Descriptions wrap without losing content", layout.hasVisualOverflow)

            link.performTouchInput { click(layout.getBoundingBox(0).center) }
            assertEquals("The link opens its own destination exactly once", 1, opens[index])
            button.performClick()
            assertEquals("The independent button opens that destination once", 2, opens[index])
            opens.forEachIndexed { target, count ->
                assertEquals("A destination does not trigger another destination", if (target <= index) 2 else 0, count)
            }
        }
    }

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

    private fun assertLinkCard(style: UiStyle, width: Dp = 328.dp, shouldWrap: Boolean = false, hostEnabled: Boolean = false) {
        show(style, width, hostEnabled)
        val title = compose.onNodeWithText("隐私政策", useUnmergedTree = true)
        val link = compose.onNodeWithText("sleepy.qdp.qzz.io/privacy", useUnmergedTree = true)
        title.assertIsDisplayed()
        link.assertIsDisplayed()
        assertEquals(
            "The URL keeps the title's leading alignment",
            title.fetchSemanticsNode().boundsInRoot.left,
            link.fetchSemanticsNode().boundsInRoot.left,
            .1f,
        )
        val button = compose.onNodeWithTag("about_privacy_link")
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
        val card = compose.onNodeWithTag("privacy_card").fetchSemanticsNode()
        assertFalse("The card is not a second whole-row action", card.config.contains(SemanticsActions.OnClick))
        val linkBounds = link.fetchSemanticsNode().boundsInRoot
        assertTrue("The URL leaves room for its independent action", linkBounds.right <= button.fetchSemanticsNode().boundsInRoot.left + .1f)
        assertTrue("The URL stays inside the card", linkBounds.left >= card.boundsInRoot.left && linkBounds.right <= card.boundsInRoot.right)
        val layout = assertActualLink(link, "https://sleepy.qdp.qzz.io/privacy")
        assertEquals("The URL wraps only when its text column needs it", shouldWrap, layout.lineCount > 1)
        assertFalse("The complete URL fits without clipping", layout.hasVisualOverflow)
        link.performTouchInput { click(layout.getBoundingBox(0).center) }
        assertEquals("The visible URL is an actual link", 1, opens)
        button.performClick()
        assertEquals("The named glass/Material action uses that same link handler", 2, opens)
        compose.onNodeWithContentDescription("隐私政策", useUnmergedTree = true)
            .performTouchInput { click(center) }
        assertEquals("Clicking the external icon opens once", 3, opens)
    }

    private fun assertActualLink(link: SemanticsNodeInteraction, destination: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        link.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val text = layout.layoutInput.text
        val annotation = text.getLinkAnnotations(0, text.length).single()
        assertEquals(destination, (annotation.item as LinkAnnotation.Url).url)
        assertEquals("External descriptions remain visibly underlined", TextDecoration.Underline, layout.layoutInput.style.textDecoration)
        return layout
    }
}
