package com.lingion.sleepy.ui.component.liquidglass

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshots.Snapshot

/** Native content can invalidate its own RenderNode without any Compose state changing. */
@Composable
fun BindGlassAndroidViewSource(view: View?) {
    val host = LocalGlassHost.current
    DisposableEffect(view, host) {
        if (view == null || host == null) return@DisposableEffect onDispose { }
        var tree: ViewTreeObserver? = null
        val listener = ViewTreeObserver.OnPreDrawListener {
            if (view.isDirty) {
                // Read epochs outside observation. A normal glass redraw does not dirty the
                // native child again, so it must not become an unconditional frame ticker.
                Snapshot.withoutReadObservation {
                    host.sources.toList().forEach { it.nativeGeneration++ }
                }
            }
            true
        }
        fun attach() {
            tree = view.viewTreeObserver
            tree?.addOnPreDrawListener(listener)
        }
        fun detach() {
            tree?.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
            tree = null
        }
        val attachment = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { attach() }
            override fun onViewDetachedFromWindow(v: View) { detach() }
        }
        view.addOnAttachStateChangeListener(attachment)
        if (view.isAttachedToWindow) attach()
        onDispose {
            detach()
            view.removeOnAttachStateChangeListener(attachment)
        }
    }
}
