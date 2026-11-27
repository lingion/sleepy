package com.lingion.sleepy.ui.component.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/** Attached Material navigation items with the same glass backing as a sheet's top section. */
@Composable
internal fun GlassAttachedNavigationBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onHeightChanged: (Int) -> Unit = {},
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Measure NavigationBar with its current insets every time. The reported
            // height reserves page space; it must never constrain the bar itself.
            .onSizeChanged { onHeightChanged(it.height) },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(horizontal = 8.dp)
                .glassControl(
                    shape = NativeGlassSheetTopSectionShape,
                    enabled = false,
                    registerPane = false,
                    role = GlassMaterialRole.Sheet,
                    backdropOverride = backdrop,
                ),
        )
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = windowInsets,
            content = content,
        )
    }
}
