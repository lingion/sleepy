package com.lingion.sleepy.ui.component.liquidglass

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf
import java.util.IdentityHashMap

/** Actual software window draws for tests that depend on work posted by content drawing. */
internal fun createSoftwareWindowComposeRule(): ComposeContentTestRule {
    val compose = createComposeRule()
    // Configure the Activity before launch, so both its window and Popup use
    // software submission instead of awaiting unavailable HWUI commit callbacks.
    val softwareActivity = object : ExternalResource() {
        private lateinit var original: ActivityInfo

        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val packageManager = context.packageManager
            original = packageManager.getActivityInfo(ComponentName(context, ComponentActivity::class.java), 0)
            shadowOf(packageManager).addOrUpdateActivity(ActivityInfo(original).apply {
                flags = flags and ActivityInfo.FLAG_HARDWARE_ACCELERATED.inv()
            })
        }

        override fun after() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addOrUpdateActivity(original)
        }
    }
    val softwareFrames = SoftwareWindowFrames()
    val renderWindows = object : ExternalResource() {
        override fun before() {
            compose.registerIdlingResource(softwareFrames)
        }

        override fun after() {
            try {
                compose.unregisterIdlingResource(softwareFrames)
            } finally {
                softwareFrames.dispose()
            }
        }
    }
    val rules = RuleChain.outerRule(softwareActivity).around(compose).around(renderWindows)
    return object : ComposeContentTestRule by compose {
        override fun apply(base: Statement, description: Description): Statement = rules.apply(base, description)
    }
}

// Compose's Robolectric idler drives layout, but skips draw passes. Render actual
// dirty window roots and let posted work settle on the next idle poll, without
// advancing the Compose clock or invoking application callbacks directly.
private class SoftwareWindowFrames : IdlingResource {
    private val buffers = IdentityHashMap<View, Bitmap>()

    override val isIdleNow: Boolean
        get() {
            check(Looper.myLooper() == Looper.getMainLooper()) { "Window draws must run on the UI thread" }
            val roots = WindowInspector.getGlobalWindowViews().filter {
                it.isAttachedToWindow && it.width > 0 && it.height > 0
            }
            buffers.keys.filter { previous -> roots.none { it === previous } }.forEach {
                buffers.remove(it)?.recycle()
            }
            val dirty = roots.filter { it !in buffers || it.isDirty }
            dirty.forEach { root ->
                check(!root.isHardwareAccelerated) { "The window must use the software test fixture" }
                val previous = buffers[root]
                val bitmap = previous?.takeIf { it.width == root.width && it.height == root.height }
                    ?: Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                        previous?.recycle()
                        buffers[root] = it
                    }
                root.draw(Canvas(bitmap))
            }
            return dirty.isEmpty()
        }

    fun dispose() {
        buffers.values.forEach(Bitmap::recycle)
        buffers.clear()
    }
}
