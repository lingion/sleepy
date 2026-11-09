package com.lingion.sleepy.data.jw

/**
 * 教务直连「最近导入的学校」纯逻辑 — 只存 [JwSchoolInfo.stableKey]，不存名称/URL。
 *
 * 目录条目会被重排或改名，stableKey 是唯一稳定的身份；显示时再回目录解析，
 * 因此目录删掉的学校会自动从卡片消失，不会留下一个点不动的死条目。
 * 数据由 AppPrefs 以 JSON 数组落 SharedPreferences，本对象不碰 Android API。
 */
object RecentJwSchools {
    const val MAX_ENTRIES = 3

    /** 一次成功导入后把 key 提到最前，去重并截断；空 key 不写入也不打乱既有顺序。 */
    fun merge(existing: List<String>, importedKey: String, limit: Int = MAX_ENTRIES): List<String> {
        require(limit > 0) { "recent school limit must be positive" }
        if (importedKey.isBlank()) return sanitize(existing, limit)
        val next = ArrayList<String>(limit + 1)
        next.add(importedKey)
        for (key in existing) {
            if (key.isBlank() || key == importedKey || key in next) continue
            next.add(key)
            if (next.size == limit) break
        }
        return next.take(limit)
    }

    /** 读盘兜底：坏数据、重复项、超长列表都在这里收敛。 */
    fun sanitize(keys: List<String>, limit: Int = MAX_ENTRIES): List<String> {
        require(limit > 0) { "recent school limit must be positive" }
        return keys.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .take(limit)
            .toList()
    }

    /** 按记录顺序还原成当前目录里的学校；已下架的 key 直接跳过。 */
    fun resolve(
        keys: List<String>,
        schools: List<JwSchoolInfo>,
        limit: Int = MAX_ENTRIES
    ): List<JwSchoolInfo> {
        val byKey = schools.associateBy { it.stableKey }
        return sanitize(keys, limit).mapNotNull { byKey[it] }
    }
}
