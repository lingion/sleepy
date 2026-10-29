@file:Suppress("unused")

package com.xiaomanjun.sleepdownschedule

/**
 * Stub BuildConfig for ported glass source files that reference
 * com.xiaomanjun.sleepdownschedule.BuildConfig.
 *
 * The real SleepDown app generates this via buildConfigField in build.gradle.kts.
 * We mirror the default values here so the ported code compiles without modifying
 * the glass source files.
 */
object BuildConfig {
    const val DEBUG: Boolean = true
    const val GLASS_FIXED_MORPH: Boolean = false
    const val GLASS_OCCLUSION_MODE: String = "legacy"
    const val BUILD_TYPE: String = "debug"
}
