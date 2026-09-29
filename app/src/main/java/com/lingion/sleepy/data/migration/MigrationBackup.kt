package com.lingion.sleepy.data.migration

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sleepy 全量迁移备份协议常量与 manifest 模型。
 * `.sleepybackup` 是单文件 ZIP 容器；`.sleepy` 仍保留给 sleepy-v1。
 */
object MigrationBackup {
    const val FILE_EXTENSION = "sleepybackup"
    const val FORMAT = "sleepy-migration"
    const val VERSION = 1
    const val ENTRY_MANIFEST = "manifest.json"
    const val ENTRY_MODULES_DIR = "modules"
    const val ENTRY_EXTENSIONS_DIR = "extensions"

    @Serializable
    data class Manifest(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val createdAt: Long,
        val modules: List<String>
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeManifest(manifest: Manifest): String =
        json.encodeToString(Manifest.serializer(), manifest)

    fun decodeManifest(text: String): Manifest =
        json.decodeFromString(Manifest.serializer(), text)
}

enum class MigrationModule(val entryName: String) {
    DATABASE("database"),
    PREFERENCES("preferences"),
    WIDGETS("widgets"),
    PERSISTED_STATE("persisted_state"),
    CREDENTIALS("credentials");

    companion object {
        private val byName = entries.associateBy { it.entryName }
        fun fromEntryName(name: String): MigrationModule? = byName[name]
    }
}

object MigrationModuleDeps {
    val REQUIRED: Map<MigrationModule, Set<MigrationModule>> = mapOf(
        MigrationModule.DATABASE to setOf(MigrationModule.PERSISTED_STATE)
    )

    fun resolve(selected: Set<MigrationModule>): Set<MigrationModule> {
        val out = selected.toMutableSet()
        val pending = selected.toMutableList()
        while (pending.isNotEmpty()) {
            val module = pending.removeAt(pending.size - 1)
            for (dependency in REQUIRED[module].orEmpty()) {
                if (out.add(dependency)) pending += dependency
            }
        }
        return out
    }
}

class MigrationPackageException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class MigrationPackageContent(
    val manifest: MigrationBackup.Manifest,
    val modules: Map<MigrationModule, String>,
    val unknownModules: List<String>
)
