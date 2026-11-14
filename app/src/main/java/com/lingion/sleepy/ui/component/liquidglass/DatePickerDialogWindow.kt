package com.lingion.sleepy.ui.component.liquidglass

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Call inside the actual date dialog, never from its launching composition. */
@Composable
internal fun DatePickerDialogWindowViewport() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window) {
        val originalWidth = window.attributes.width
        val originalHeight = window.attributes.height
        // Keep the available viewport independent of AnimatedContent's changing
        // output height. A wrap-content window can otherwise feed its current
        // short input-mode height back into the expanding calendar's constraints.
        // DialogLayout still measures its Compose child with AT_MOST and centers
        // that child's actual bounds, so the visible pane remains wrap-content.
        // Width, system-inset policy and the platform's IME handling stay intact.
        window.setLayout(originalWidth, WindowManager.LayoutParams.MATCH_PARENT)
        onDispose { window.setLayout(originalWidth, originalHeight) }
    }
}
