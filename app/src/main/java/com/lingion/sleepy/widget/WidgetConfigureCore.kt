package com.lingion.sleepy.widget

/**
 * Pure-JVM core of [WidgetConfigureActivity] — issue #24 Feature 1 (R4/R5).
 *
 * The activity has exactly two paths:
 * - First add (no prefs entry for the widget id): write the sentinel binding
 *   and finish OK with no UI (auto-finish, R5) — third-party launchers with
 *   flaky configure flows never see a blocking dialog on add.
 * - Any later entry (sentinel or a real binding present): show the edit
 *   screen (R4 reconfigure route).
 *
 * All branching lives here so it is unit-testable without Android. The
 * activity is a thin shell: read the binding, ask this core, act.
 */
internal object WidgetConfigureCore {

    /**
     * Sentinel table id written on first add meaning "follow the app-wide
     * default table". Receivers treat it as no-binding at render time
     * (no table has id 0 — Room autoincrement starts at 1), and
     * [WidgetEditCore.displayBinding] translates it to "Default" in the UI.
     */
    const val FIRST_ADD_BINDING: Long = 0L

    /**
     * True when [existingBinding] is absent, i.e. this widget id is being
     * added for the first time → auto-finish with the sentinel binding.
     * A sentinel (0L) or any real binding means the widget already exists
     * → the activity must open the editor instead of re-auto-finishing
     * (otherwise the long-press edit route would silently do nothing).
     */
    fun isFirstAdd(existingBinding: Long?): Boolean = existingBinding == null
}
