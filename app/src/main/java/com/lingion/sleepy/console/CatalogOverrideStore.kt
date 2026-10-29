package com.lingion.sleepy.console

import android.content.Context
import com.lingion.sleepy.data.jw.JwSchoolInfo
import org.json.JSONObject
import java.io.File

/**
 * schools.json 运行时行覆盖层（SPEC §3 /v1/data/catalog；红线 §5.2：release 隐藏模式只读）。
 *
 * 设计（Task 2 brief）：内存 map + 持久化到 `filesDir/console_catalog_overrides.json`；
 * assets 的 schools.json 永远不动——覆盖层在读取链（JwImportViewModel.loadSchools）
 * 套用，按 name 整行覆盖 status/type/url 三个字段，未提供的字段保持原值。
 *
 * 进程内为唯一真源：ConsoleBootstrap 启动时 [init] 载入文件；
 * [put] 在 debug 构建落盘，release 解锁态由 handler 提前 403 拦截、物理到不了这里。
 * name 作 key 用原文（目录内 name 唯一性由 schools.json 生成闸保证）。
 */
object CatalogOverrideStore {

    /** 单行覆盖 — null 字段 = 不覆盖。 */
    data class Override(
        val status: String? = null,
        val type: String? = null,
        val url: String? = null,
    ) {
        fun isEmpty(): Boolean = status == null && type == null && url == null
    }

    private val overrides = LinkedHashMap<String, Override>()
    private var persistFile: File? = null
    private var loaded = false

    /** 装配点：定位持久化文件并载入既有覆盖（重复调用幂等，只读一次盘）。 */
    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val f = File(context.applicationContext.filesDir, FILE_NAME)
        persistFile = f
        if (f.exists()) {
            runCatching { readFrom(f) } // 文件损坏 → 空覆盖层，读取链照常走 assets
        }
    }

    /** JVM 单测注入文件位置（不依赖 Android Context）。 */
    @Synchronized
    fun initForTest(file: File) {
        loaded = true
        persistFile = file
        overrides.clear()
        if (file.exists()) runCatching { readFrom(file) }
    }

    /** 仅测试复位：清内存 + 去文件绑定。 */
    @Synchronized
    fun resetForTest() {
        overrides.clear()
        persistFile = null
        loaded = false
    }

    /** 仅测试：清内存但保留文件绑定，模拟进程重启（文件还在、内存归零）。 */
    @Synchronized
    fun clearMemoryForTest() {
        overrides.clear()
    }

    /** 从持久化文件重载进内存。文件不存在/损坏 → 清空。 */
    @Synchronized
    fun reload() {
        overrides.clear()
        val f = persistFile ?: return
        if (f.exists()) runCatching { readFrom(f) }
    }

    @Synchronized
    fun get(name: String): Override? = overrides[name]

    @Synchronized
    fun all(): Map<String, Override> = LinkedHashMap(overrides)

    /**
     * 写入一行覆盖。persist=true 且已绑定文件时落盘；落盘失败回滚内存并返回 false。
     * 是否允许持久化由调用方裁决（debug 构建），本层不感知构建类型。
     */
    @Synchronized
    fun put(name: String, status: String?, type: String?, url: String?, persist: Boolean): Boolean {
        val o = Override(status, type, url)
        if (o.isEmpty()) return false
        val prev = overrides[name]
        overrides[name] = o
        if (persist) {
            val f = persistFile
            if (f != null && !writeTo(f)) {
                if (prev == null) overrides.remove(name) else overrides[name] = prev
                return false
            }
        }
        return true
    }

    /** 删除一行覆盖（可选，留给 session/reset 与纠错用）。 */
    @Synchronized
    fun clear(name: String, persist: Boolean): Boolean {
        if (overrides.remove(name) == null) return false
        if (persist) persistFile?.let { writeTo(it) }
        return true
    }

    /**
     * 读取链套用：把覆盖合并进 assets 解析出的目录行（按 name 匹配）。
     * 无覆盖的条目原样透传（同一实例引用，零开销）。
     * @Synchronized：overrides 是 LinkedHashMap，写入方（[put]）与读迭代（这里）
     * 分属 console worker 线程和 UI/IO 协程，必须同锁防结构性撕裂。
     */
    @Synchronized
    fun applyTo(schools: List<JwSchoolInfo>): List<JwSchoolInfo> =
        if (overrides.isEmpty()) schools
        else schools.map { s ->
            val o = overrides[s.name] ?: return@map s
            s.copy(
                status = o.status?.let(JwSchoolInfo::normalizeStatus) ?: s.status,
                type = o.type ?: s.type,
                url = o.url ?: s.url,
            )
        }

    private fun readFrom(f: File) {
        overrides.clear()
        val root = JSONObject(f.readText(Charsets.UTF_8))
        for (name in root.keys()) {
            val row = root.optJSONObject(name) ?: continue
            val o = Override(
                status = if (row.isNull(F_STATUS)) null else row.optString(F_STATUS, "").ifBlank { null },
                type = if (row.isNull(F_TYPE)) null else row.optString(F_TYPE, "").ifBlank { null },
                url = if (row.isNull(F_URL)) null else row.optString(F_URL, "").ifBlank { null },
            )
            if (!o.isEmpty()) overrides[name] = o
        }
    }

    private fun writeTo(f: File): Boolean = runCatching {
        val root = JSONObject()
        for ((name, o) in overrides) {
            root.put(name, JSONObject()
                .put(F_STATUS, o.status ?: JSONObject.NULL)
                .put(F_TYPE, o.type ?: JSONObject.NULL)
                .put(F_URL, o.url ?: JSONObject.NULL))
        }
        f.writeText(root.toString(), Charsets.UTF_8)
        true
    }.getOrDefault(false)

    private const val FILE_NAME = "console_catalog_overrides.json"
    private const val F_STATUS = "status"
    private const val F_TYPE = "type"
    private const val F_URL = "url"
}
