package com.lingion.sleepy.data.migration

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** .sleepybackup ZIP 容器读写。坏包在写入本机数据前被拒绝。 */
object MigrationPackageCodec {
    fun moduleEntryName(module: MigrationModule): String =
        "${MigrationBackup.ENTRY_MODULES_DIR}/${module.entryName}.json"

    fun write(modules: Map<MigrationModule, String>, createdAt: Long): ByteArray {
        require(modules.isNotEmpty()) { "migration package needs at least one module" }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val manifest = MigrationBackup.Manifest(
                createdAt = createdAt,
                modules = modules.keys.map { it.entryName }.sorted()
            )
            zip.putNextEntry(ZipEntry(MigrationBackup.ENTRY_MANIFEST))
            zip.write(MigrationBackup.encodeManifest(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (module in modules.keys.sortedBy { it.entryName }) {
                zip.putNextEntry(ZipEntry(moduleEntryName(module)))
                zip.write(modules.getValue(module).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): MigrationPackageContent {
        val known = mutableMapOf<MigrationModule, String>()
        val unknown = mutableListOf<String>()
        var manifest: MigrationBackup.Manifest? = null
        val seenEntries = mutableSetOf<String>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name.startsWith('/') || name.contains("..")) {
                        throw MigrationPackageException("illegal zip entry: $name")
                    }
                    if (!seenEntries.add(name)) {
                        throw MigrationPackageException("duplicate zip entry: $name")
                    }
                    when {
                        name == MigrationBackup.ENTRY_MANIFEST -> {
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            manifest = runCatching { MigrationBackup.decodeManifest(text) }
                                .getOrElse { throw MigrationPackageException("manifest unreadable", it) }
                        }
                        name.startsWith("${MigrationBackup.ENTRY_MODULES_DIR}/") && name.endsWith(".json") -> {
                            val base = name.removePrefix("${MigrationBackup.ENTRY_MODULES_DIR}/")
                                .removeSuffix(".json")
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            val module = MigrationModule.fromEntryName(base)
                            if (module == null) unknown += base else known[module] = text
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: MigrationPackageException) {
            throw error
        } catch (error: Exception) {
            throw MigrationPackageException("backup ZIP unreadable", error)
        }
        val m = manifest ?: throw MigrationPackageException("manifest.json missing")
        if (m.format != MigrationBackup.FORMAT) {
            throw MigrationPackageException("unknown backup format: ${m.format}")
        }
        if (m.version > MigrationBackup.VERSION) {
            throw MigrationPackageException("backup made by newer Sleepy (v${m.version})")
        }
        return MigrationPackageContent(m, known, unknown.sorted())
    }
}
