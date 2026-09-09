package com.lingion.sleepy.widget

import android.content.Context
import android.content.Intent
import com.lingion.sleepy.MainActivity

/**
 * Single source of truth for widget → app tap routing.
 *
 * Three render paths (bitmap face via [RemoteViewsWidgetHelper.renderAndPush],
 * scrollable template via [RemoteViewsWidgetHelper.pushScrollable], and the
 * grid provider's own face via [WeekGridWidgetProvider]) previously
 * duplicated the same intent construction inline. Centralizing it pins the
 * per-instance routing contract that issue #24 Feature 1 depends on:
 *
 * - requestCode = widgetId: with FLAG_UPDATE_CURRENT, PendingIntents are
 *   keyed on (requestCode, intent-filter-equality); a shared code would let
 *   one instance's update clobber another instance's tap action.
 * - flags = NEW_TASK | CLEAR_TOP: widget taps launch/reuse the app task.
 * - target = MainActivity (the app renders the bound schedule there).
 *
 * Behavior is identical to the previously duplicated inline form.
 */
internal object WidgetRoutes {

    /**
     * PendingIntent requestCode for a widget tap — must be unique per widget
     * instance so updates to one widget never overwrite another's tap action.
     */
    fun tapRequestCode(widgetId: Int): Int = widgetId

    /** Flags for the widget tap intent (launch or reuse the app task). */
    const val TAP_FLAGS: Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

    /** The tap intent shared by every widget face of one instance. */
    fun tapIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply { flags = TAP_FLAGS }
}