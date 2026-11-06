package com.kyant.backdrop

// Each effect sets uniforms then immediately snapshots a native RenderEffect on this thread.
// Other render/preview threads must never mutate the same RuntimeShader builder concurrently.
private val effectShaders = object : ThreadLocal<EffectShaderRegistry>() {
    override fun initialValue() = EffectShaderRegistry()
}

internal actual fun sharedEffectShaderRegistry(): EffectShaderRegistry = checkNotNull(effectShaders.get())
