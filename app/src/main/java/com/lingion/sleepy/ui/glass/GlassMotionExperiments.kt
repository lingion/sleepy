package com.lingion.sleepy.ui.glass

import com.xiaomanjun.sleepdownschedule.BuildConfig

/** Release stays on the accepted lifecycle until phone AND tablet traces validate a candidate. */
internal object GlassMotionExperiments {
    val fixedMorph: Boolean = BuildConfig.GLASS_FIXED_MORPH
    val retainMaterialNodes: Boolean = BuildConfig.GLASS_OCCLUSION_MODE == "retained"
    val continuousMaterialDrawing: Boolean = BuildConfig.GLASS_OCCLUSION_MODE == "live"
}
