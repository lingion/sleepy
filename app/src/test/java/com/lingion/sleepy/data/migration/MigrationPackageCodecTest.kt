package com.lingion.sleepy.data.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPackageCodecTest {
    @Test
    fun `write and read preserves manifest and module JSON`() {
        val bytes = MigrationPackageCodec.write(
            modules = mapOf(
                MigrationModule.PREFERENCES to "{\"theme\":\"dark\"}",
                MigrationModule.DATABASE to "{\"tables\":[]}",
                MigrationModule.PERSISTED_STATE to "{\"drafts\":[]}"
            ),
            createdAt = 1234L
        )

        val result = MigrationPackageCodec.read(bytes)

        assertEquals(MigrationBackup.FORMAT, result.manifest.format)
        assertEquals(1, result.manifest.version)
        assertEquals(1234L, result.manifest.createdAt)
        assertEquals("{\"theme\":\"dark\"}", result.modules[MigrationModule.PREFERENCES])
        assertEquals(listOf("database", "persisted_state", "preferences"), result.manifest.modules)
    }

    @Test
    fun `unknown module is reported and ignored`() {
        val known = MigrationPackageCodec.write(
            mapOf(MigrationModule.PREFERENCES to "{}"),
            createdAt = 1L
        )
        val extra = known.copyOf(known.size)
        // Unknown-entry coverage uses a valid ZIP assembled through the same public container.
        // The reader's forward-compatibility behavior is asserted by a manifest-only package below.
        assertTrue(extra.isNotEmpty())
        val result = MigrationPackageCodec.read(unknownModulePackage())
        assertEquals(listOf("future_settings"), result.unknownModules)
        assertTrue(result.modules.isEmpty())
    }

    @Test
    fun `invalid bytes are rejected before import`() {
        assertThrows(MigrationPackageException::class.java) {
            MigrationPackageCodec.read("not a zip".toByteArray())
        }
    }

    @Test
    fun `database selection receives persisted state dependency`() {
        assertEquals(
            setOf(MigrationModule.DATABASE, MigrationModule.PERSISTED_STATE),
            MigrationModuleDeps.resolve(setOf(MigrationModule.DATABASE))
        )
    }

    private fun unknownModulePackage(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry(MigrationBackup.ENTRY_MANIFEST))
            zip.write(
                MigrationBackup.encodeManifest(
                    MigrationBackup.Manifest(createdAt = 1L, modules = listOf("future_settings"))
                ).toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("modules/future_settings.json"))
            zip.write("{\"value\":true}".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
