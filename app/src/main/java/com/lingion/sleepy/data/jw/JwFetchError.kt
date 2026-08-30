package com.lingion.sleepy.data.jw

import android.content.Context
import com.lingion.sleepy.R
import org.json.JSONObject

/**
 * T11: WebView 内 fetch 模式的错误分类与桥协议。
 *
 * ZF_NEW_FETCH_JS 与 QZ_FETCH_JS 把错误分成四类
 * (会话过期 / 解析失败 / 网络异常 / 学期无课)，而不是笼统 ok:false。
 * when over sealed class 强制穷尽，switch 漏分支编译期就会报错。
 */
sealed class FetchErrorKind {
    /** 后端返回 HTML 登录页 / 302 CAS / JSON.parse 抛异常 —— 文案:重新登录 */
    object SessionExpired : FetchErrorKind()
    /** 拿到数据但 Jw*Parser 报 0 课 + 后端不是登录页 —— 文案:可能未到课表页 */
    object ParseFailed : FetchErrorKind()
    /** fetch reject / HTTP 4xx/5xx / TypeError —— 文案:网络异常 */
    object Network : FetchErrorKind()
    /** 拿到数据且 parser 出 0 门但响应里明确说"本学期无课" —— 文案:切换学期 */
    object Empty : FetchErrorKind()
}

data class FetchError(
    val kind: FetchErrorKind,
    val message: String,
    val schoolType: String? = null,
    val url: String? = null,
) {
    fun userMessage(ctx: Context): String = when (kind) {
        FetchErrorKind.SessionExpired -> ctx.getString(R.string.jw_fetch_session_expired)
        FetchErrorKind.ParseFailed -> ctx.getString(R.string.jw_fetch_parse_failed)
        FetchErrorKind.Network -> ctx.getString(R.string.jw_fetch_network)
        FetchErrorKind.Empty -> ctx.getString(R.string.jw_fetch_empty)
    }
}

object JwFetchError {
    private const val KEY_KIND = "kind"
    private const val KEY_MSG = "msg"
    private const val KEY_TYPE = "type"
    private const val KEY_URL = "url"

    fun toJson(err: FetchError): String {
        val kindStr = when (err.kind) {
            FetchErrorKind.SessionExpired -> "SESSION_EXPIRED"
            FetchErrorKind.ParseFailed -> "PARSE_FAILED"
            FetchErrorKind.Network -> "NETWORK"
            FetchErrorKind.Empty -> "EMPTY"
        }
        return JSONObject()
            .put(KEY_KIND, kindStr)
            .put(KEY_MSG, err.message)
            .put(KEY_TYPE, err.schoolType ?: JSONObject.NULL)
            .put(KEY_URL, err.url ?: JSONObject.NULL)
            .toString()
    }

    fun fromJson(s: String): FetchError? = try {
        val obj = JSONObject(s)
        val kind = when (obj.optString(KEY_KIND)) {
            "SESSION_EXPIRED" -> FetchErrorKind.SessionExpired
            "PARSE_FAILED" -> FetchErrorKind.ParseFailed
            "NETWORK" -> FetchErrorKind.Network
            "EMPTY" -> FetchErrorKind.Empty
            else -> return null
        }
        FetchError(
            kind = kind,
            message = obj.optString(KEY_MSG),
            schoolType = obj.optString(KEY_TYPE).ifBlank { null },
            url = obj.optString(KEY_URL).ifBlank { null },
        )
    } catch (e: Exception) {
        null
    }
}
