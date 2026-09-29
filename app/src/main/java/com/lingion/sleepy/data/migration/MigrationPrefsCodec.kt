package com.lingion.sleepy.data.migration

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json

/**
 * SharedPreferences 全量快照 codec — preferences / widgets / persisted_state 三个模块共用。
 *
 * 值带类型信封 (bool/int/long/float/string/string_set): 导入端按信封还原,
 * 不依赖 Android 端 getString-then-parse 的猜测; 未知键原样保留。
 */
object MigrationPrefsCodec {

    @Serializable
    data class PrefValue(
        val type: PrefType,
        val bool: Boolean = false,
        /** int 与 long 统一走这里 (JS 端 number 精度内, iOS 端 Int64)。 */
        val number: Long = 0L,
        val float: Float = 0f,
        val string: String = "",
        val stringSet: List<String> = emptyList(),
    )

    @Serializable
    enum class PrefType { BOOL, INT, LONG, FLOAT, STRING, STRING_SET }

    @Serializable
    data class PrefsFileSnapshot(val entries: Map<String, PrefValue> = emptyMap())

    @Serializable
    data class PrefsSnapshot(val files: Map<String, PrefsFileSnapshot> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(snapshot: PrefsSnapshot): String =
        json.encodeToString(PrefsSnapshot.serializer(), snapshot)

    fun decode(text: String): PrefsSnapshot =
        json.decodeFromString(PrefsSnapshot.serializer(), text)

    /** 从单个 SharedPreferences 收集全量键值 (类型按运行时实际值判别)。 */
    fun collect(prefs: SharedPreferences): PrefsFileSnapshot {
        val entries = LinkedHashMap<String, PrefValue>()
        for ((key, raw) in prefs.all) {
            val value = when (raw) {
                is Boolean -> PrefValue(PrefType.BOOL, bool = raw)
                is Int -> PrefValue(PrefType.INT, number = raw.toLong())
                is Long -> PrefValue(PrefType.LONG, number = raw)
                is Float -> PrefValue(PrefType.FLOAT, float = raw)
                is String -> PrefValue(PrefType.STRING, string = raw)
                is Set<*> -> @Suppress("UNCHECKED_CAST") PrefValue(
                    PrefType.STRING_SET,
                    stringSet = (raw as? Set<String>).orEmpty().sorted(),
                )
                else -> null // 未来新类型: 忽略并继续, 不阻断导出
            }
            if (value != null) entries[key] = value
        }
        return PrefsFileSnapshot(entries)
    }

    /**
     * 写回单个 SharedPreferences。
     * @param merge true=仅覆盖同名键; false=先 clear 再写入 (模块级全量替换)。
     */
    fun apply(prefs: SharedPreferences, snapshot: PrefsFileSnapshot, merge: Boolean) {
        val editor = prefs.edit()
        if (!merge) editor.clear()
        for ((key, value) in snapshot.entries) {
            when (value.type) {
                PrefType.BOOL -> editor.putBoolean(key, value.bool)
                PrefType.INT -> editor.putInt(key, value.number.toInt())
                PrefType.LONG -> editor.putLong(key, value.number)
                PrefType.FLOAT -> editor.putFloat(key, value.float)
                PrefType.STRING -> editor.putString(key, value.string)
                PrefType.STRING_SET -> editor.putStringSet(key, value.stringSet.toSet())
            }
        }
        editor.apply()
    }
}

/** 各模块覆盖的 SharedPreferences 文件清单 (debug_import 为调试通道, 永不迁移)。 */
object MigrationPrefsFileMap {
    val PREFERENCES: List<String> = listOf(
        "sleepy_prefs",     // AppPrefs 主文件 (212+ 键)
        "custom_themes",    // 自定义主题
    )
    val WIDGETS: List<String> = listOf(
        "widget_bindings",
        "widget_scroll_prefs",
        "widget_compact_window_prefs",
        "widget_today_nav",
    )
    val PERSISTED_STATE: List<String> = emptyList() // Android 端当前无独立持久态; 预留给两端未来扩展

    fun filesOf(module: MigrationModule): List<String> = when (module) {
        MigrationModule.PREFERENCES -> PREFERENCES
        MigrationModule.WIDGETS -> WIDGETS
        MigrationModule.PERSISTED_STATE -> PERSISTED_STATE
        else -> emptyList()
    }
}
