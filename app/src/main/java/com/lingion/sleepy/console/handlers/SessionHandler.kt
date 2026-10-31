package com.lingion.sleepy.console.handlers

import android.app.Application
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.console.CatalogOverrideStore
import com.lingion.sleepy.console.ConsoleBootstrap
import com.lingion.sleepy.console.Envelope
import com.lingion.sleepy.console.MockRuleStore
import com.lingion.sleepy.console.TokenStore
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.AppClock
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * POST /v1/session/reset — 会话归零（Task 8, SPEC §session）：
 * 调试残留一键清空, 用户真实数据零触碰。
 *
 * 判据（brief）：
 *  - 课表：仅删 name 以 "console:" 前缀的表（agent 经 /v1/data 造的表按约定打标）;
 *    其余表（含 DebugTriggers 产物）一律保留 — [planTableDeletions] 纯函数 JVM 锁;
 *  - ImportDraft：全清（草稿是调试过程物, 无用户手工内容风险）;
 *  - 虚拟时钟偏移归零; jw-mock 规则清空; catalog 覆盖层清空（红线: release
 *    解锁态覆盖层本来不可写, 清它 = debug 会话卫生）;
 *  - AppLog/审计环保留本轮（reset 响应里回 summary, 清空反而丢证据）。
 *
 * 不碰 prefs（裁决：prefs 恢复默认属"改用户设置"面, SPEC 未授权 reset 越界;
 * console 写 prefs 本就限 console_ 前缀拒写, 无残留可清）。
 */
object SessionHandler {

    /** console 造表约定前缀（与 AGENT-SKILL.md 同步, 一致性测试互锁）。 */
    const val TABLE_PREFIX = "console:"

    data class ConsoleTable(val id: Long, val name: String)

    fun planTableDeletions(tables: List<ConsoleTable>): List<ConsoleTable> =
        tables.filter { it.name.startsWith(TABLE_PREFIX) }

    /** 进程内 volatile 态归零（时钟/mock）— JVM 可测半区。 */
    fun resetVolatileState() {
        AppClock.reset()
        MockRuleStore.clear()
    }

    fun registerAll(app: Application) {
        ConsoleBootstrap.addHandler("/v1/session/reset", "POST", requiresToken = true) {
            handle(app)
        }
    }

    private fun handle(app: Application): String = try {
        // volatile 面归零走本函数唯一入口(时钟+mock), 摘要字段与实现不脱钩
        resetVolatileState()
        val repo = SleepyApp.get().repository
        val summary = runBlocking {
            val all = repo.getAllTables().map { ConsoleTable(it.id, it.name) }
            val doomed = planTableDeletions(all)
            doomed.forEach { repo.deleteTable(it.id) }
            // 草稿全清（调试过程物, 无用户手工内容风险）
            SleepyApp.get().importDraftRepository.clearAll()
            // catalog 覆盖层（含持久文件删除）
            val catalogCleared = CatalogOverrideStore.clearAll()
            JSONObject()
                .put("tables_deleted", JSONArray(doomed.map { it.name }))
                .put("tables_kept", all.size - doomed.size)
                .put("clock_reset", true)
                .put("mock_rules_cleared", true)
                .put("catalog_overrides_cleared", catalogCleared)
        }
        summary.put("token_ttl_ms", TokenStore.TOKEN_TTL_MS)
        Envelope.ok(summary.toString())
    } catch (t: Throwable) {
        Envelope.err(500, "session reset failed: ${t.message}")
    }
}
