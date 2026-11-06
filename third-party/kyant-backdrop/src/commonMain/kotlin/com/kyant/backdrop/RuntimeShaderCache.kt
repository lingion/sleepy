// Modified for SleepDown on 2026-10-09; Nexio shared-effect program strategy (see README.md).
package com.kyant.backdrop

import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl(shareEffects: Boolean = false) : RuntimeShaderCache {

    private val shared = if (shareEffects) sharedEffectShaderRegistry() else null
    private val runtimeShaders = mutableMapOf<ShaderProgramKey, RuntimeShader>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        val program = ShaderProgramKey(key, string)
        return runtimeShaders.getOrPut(program) {
            shared?.acquire(program) ?: RuntimeShader(string)
        }
    }

    fun clear() {
        if (shared != null) runtimeShaders.keys.forEach(shared::release)
        runtimeShaders.clear()
    }
}

internal data class ShaderProgramKey(val name: String, val source: String)

/**
 * RenderEffect snapshots uniforms immediately, so effect builders may share compiled programs.
 * Direct-paint highlight shaders retain node ownership. Keep this registry on the drawing thread
 * and release programs with their last consumer instead of retaining an unbounded process cache.
 */
internal class EffectShaderRegistry {
    private class Entry(val shader: RuntimeShader, var owners: Int = 1)
    private val programs = mutableMapOf<ShaderProgramKey, Entry>()

    fun acquire(key: ShaderProgramKey): RuntimeShader {
        val existing = programs[key]
        if (existing != null) {
            existing.owners++
            BackdropDiagnostics.event("Shader.ProgramReused")
            return existing.shader
        }
        val shader = RuntimeShader(key.source)
        programs[key] = Entry(shader)
        BackdropDiagnostics.event("Shader.ProgramCreated")
        return shader
    }

    fun release(key: ShaderProgramKey) {
        val entry = checkNotNull(programs[key])
        if (--entry.owners == 0) {
            programs.remove(key)
            BackdropDiagnostics.event("Shader.ProgramReleased")
        }
    }
}

internal expect fun sharedEffectShaderRegistry(): EffectShaderRegistry

