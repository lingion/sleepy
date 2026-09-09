package com.lingion.sleepy.data.jw

/**
 * issue #25 — 把 typed URL 映射回 schools.json 目录条目。
 *
 * 失败链:
 *   1. 用户输入 https://one.hfut.edu.cn/ (HFUT 统一信息门户, 非教务)
 *   2. SchoolSelectScreen.UrlDirectRow 命中 → 创建自定义条目 type=null
 *   3. WebView 跳到门户 → 通用抓取 → 0 课
 *
 * 修复:
 *   解析 typed URL 的 host, 取注册域, 在目录条目中查找相同注册域的 supported+有 URL 条目。
 *   命中后用目录条目替代自定义条目 (拿到权威 URL + 协议 type)。
 *
 * 命名: 与 [JwWebViewLoginScreen.SslBypassRegistry] 一致, 委托同一 registrableDomain 实现
 * (避免两个独立实现飘移)。
 */
object SchoolDomainMatch {

    /**
     * 注册域启发式 — 与 SslBypassRegistry.registrableDomain 一致。
     * 取末两段 (edu.cn / edu.hk / ac.uk / edu.tw / edu.jp 等多段后缀取末三段)。
     */
    fun registrableDomain(host: String): String {
        val h = host.lowercase().trim().trimEnd('.')
        if (h.isEmpty()) return ""
        val parts = h.split('.')
        if (parts.size <= 2) return h
        val secondLevel = parts[parts.size - 2]
        val tld = parts.last()
        if ((tld == "cn" || tld == "hk" || tld == "uk" || tld == "tw" || tld == "jp") &&
            secondLevel in setOf("edu", "ac", "gov", "org")
        ) {
            return parts.takeLast(3).joinToString(".")
        }
        return parts.takeLast(2).joinToString(".")
    }

    /** 抽 host: 处理 https?:// 前缀、尾部 /path、空白。空返回空串。 */
    fun hostOf(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) return ""
        val schemeEnd = u.indexOf("://").let { if (it < 0) 0 else it + 3 }
        val rest = u.substring(schemeEnd)
        val slash = rest.indexOf('/')
        val hostAndPort = if (slash < 0) rest else rest.substring(0, slash)
        val colon = hostAndPort.indexOf(':')
        return if (colon < 0) hostAndPort else hostAndPort.substring(0, colon)
    }

    /** 是否 WebVPN 重写 host (不可见原 host, 不可参与目录映射)。 */
    fun isVpnRewrite(host: String): Boolean {
        if (host.isBlank()) return false
        return host.contains("/webvpn/")
            || host.contains(".webvpn.")
            || host.startsWith("webvpn.")
    }

    /**
     * 在 [catalog] 中查找: 同注册域, supported (含 grad_supported), 有 URL。
     * WebVPN / 未知域 / 空白 host → null。
     */
    fun matchSchool(url: String, catalog: List<JwSchoolInfo>): JwSchoolInfo? {
        val host = hostOf(url)
        if (host.isBlank()) return null
        if (isVpnRewrite(host)) return null
        val targetReg = registrableDomain(host)
        if (targetReg.isBlank()) return null
        // 唯一性: 同注册域多条候选 → 返回排序第一 (schools.json 已按拼音排序, 可接受)
        return catalog.firstOrNull { s ->
            s.isSupported && s.hasUrl &&
                registrableDomain(hostOf(s.url)) == targetReg
        }
    }
}