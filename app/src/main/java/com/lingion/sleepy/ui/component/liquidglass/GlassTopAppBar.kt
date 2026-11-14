package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle

/** Material 提供槽位几何；实际按钮只组合一次，放在标题裁切层之外。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    contentPadding: PaddingValues = TopAppBarDefaults.ContentPadding,
) {
    if (LocalUiStyle.current != UiStyle.LiquidGlass || LocalGlassHost.current == null) {
        androidx.compose.material3.TopAppBar(
            title = title, modifier = modifier, navigationIcon = navigationIcon, actions = actions,
            expandedHeight = expandedHeight, windowInsets = windowInsets, colors = colors,
            scrollBehavior = scrollBehavior, contentPadding = contentPadding,
        )
        return
    }
    // Material3's single-row app bar switches at the same overlap threshold
    // and animates with the theme's DefaultEffects motion token. Own that one
    // fill here so later sibling lenses can consume it without recording the
    // title, icons, or the optical controls themselves.
    val targetColor by remember(colors, scrollBehavior) {
        derivedStateOf {
            if ((scrollBehavior?.state?.overlappedFraction ?: 0f) > 0.01f) colors.scrolledContainerColor
            else colors.containerColor
        }
    }
    val backingColor by animateColorAsState(
        targetColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "GlassTopAppBarContainer",
    )
    val material = rememberGlassMaterialScope(RectangleShape)
    val positions = remember { ToolbarPositions() }
    SubcomposeLayout(
        modifier
            .glassSurfaceBackground(
                if (backingColor == Color.Unspecified) Color.Transparent else backingColor,
                RectangleShape,
                materialScope = material,
            )
            .onPlaced { positions.root = it }
            .semantics { isTraversalGroup = true },
    ) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val navigation = subcompose(ToolbarPart.Navigation) {
            GlassMaterialContent(material) {
                CompositionLocalProvider(LocalContentColor provides colors.navigationIconContentColor) {
                    Box(Modifier.semantics { traversalIndex = -1f }) { navigationIcon() }
                }
            }
        }.single().measure(loose)
        val actionButtons = subcompose(ToolbarPart.Actions) {
            GlassMaterialContent(material) {
                CompositionLocalProvider(LocalContentColor provides colors.actionIconContentColor) {
                    Row(
                        Modifier.semantics { traversalIndex = 1f },
                        verticalAlignment = Alignment.CenterVertically, content = actions,
                    )
                }
            }
        }.single().measure(loose)
        val navigationWidth = navigation.width.toDp()
        val navigationHeight = navigation.height.toDp()
        val actionsWidth = actionButtons.width.toDp()
        val actionsHeight = actionButtons.height.toDp()
        val bar = subcompose(ToolbarPart.Bar) {
            androidx.compose.material3.TopAppBar(
                title = { GlassMaterialContent(material, title) },
                navigationIcon = {
                    Box(Modifier.requiredSize(navigationWidth, navigationHeight).onPlaced {
                        positions.originOf(it)?.let { origin -> positions.navigation = origin }
                    })
                },
                actions = {
                    Box(Modifier.requiredSize(actionsWidth, actionsHeight).onPlaced {
                        positions.originOf(it)?.let { origin -> positions.actions = origin }
                    })
                },
                expandedHeight = expandedHeight, windowInsets = windowInsets,
                colors = colors.copy(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                scrollBehavior = scrollBehavior, contentPadding = contentPadding,
            )
        }.single().measure(constraints)
        layout(bar.width, bar.height) {
            bar.place(0, 0)
            // 绝对坐标来自 Material 已完成的 RTL/inset 布局，不再二次镜像。
            navigation.place(positions.navigation)
            actionButtons.place(positions.actions)
        }
    }
}

private enum class ToolbarPart { Navigation, Actions, Bar }

private class ToolbarPositions {
    var root: LayoutCoordinates? = null
    var navigation by mutableStateOf(IntOffset.Zero)
    var actions by mutableStateOf(IntOffset.Zero)

    fun originOf(child: LayoutCoordinates): IntOffset? {
        val parent = root ?: return null
        if (!parent.isAttached || !child.isAttached) return null
        return parent.localPositionOf(child, Offset.Zero).round()
    }
}
