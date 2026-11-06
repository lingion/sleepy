package com.lingion.sleepy.data.repository

import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.ImportDraftEntity
import com.lingion.sleepy.data.jw.JwImportDraftCodec
import com.lingion.sleepy.data.jw.JwImportDraftSnapshot
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import com.lingion.sleepy.util.AppClock

/** Persistence boundary for resumable academic imports. */
class ImportDraftRepository(private val db: AppDatabase) {
    private val dao = db.importDraftDao()

    fun observeAll(): Flow<List<ImportDraftEntity>> = dao.observeAll()

    suspend fun get(id: String): JwImportDraftSnapshot? =
        dao.getById(id)?.let { JwImportDraftCodec.fromJson(it.payloadJson) }

    suspend fun save(snapshot: JwImportDraftSnapshot, sourceType: String = "", sourceUrl: String = ""): String {
        val id = UUID.randomUUID().toString()
        val now = AppClock.millis()
        dao.insert(
            ImportDraftEntity(
                id = id,
                sourceType = sourceType,
                sourceUrl = sourceUrl,
                payloadJson = JwImportDraftCodec.toJson(snapshot),
                createdAt = now,
                updatedAt = now,
            )
        )
        return id
    }

    suspend fun update(id: String, snapshot: JwImportDraftSnapshot) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                payloadJson = JwImportDraftCodec.toJson(snapshot),
                updatedAt = AppClock.millis(),
            )
        )
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    /** console session/reset 用: 草稿是调试过程物, 全清(用户数据零触碰判据见 SessionHandler)。 */
    suspend fun clearAll() = dao.deleteAll()
}
