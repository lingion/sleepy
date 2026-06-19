package com.lingion.sleepy.data.jw

/**
 * 教务系统协议类型枚举。
 *
 * 基于 dIT8Zv/WakeupSchedule_BUPT (Apache-2.0) 的 Common.kt 协议类型常量
 * 简化而来，保留 sleepy v1.0.8 实际用到的子集：
 *   - QZ 强智 5 变体（HEU 用 QZ_CRAZY）
 *   - ZF 正方 3 变体
 *   - URP 2 变体
 *   - PKU 北大 / CF 青果 / BNUZ 北师珠
 *   - HELP / LOGIN / MAINTAIN 标记
 *
 * 完整 17 类 + 强智变体的语义见 https://github.com/dIT8Zv/WakeupSchedule_BUPT
 * 中 `app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/Common.kt`。
 */
object JwProtocol {

    const val TYPE_HELP = "help"
    const val TYPE_ZF = "zf"
    const val TYPE_ZF_1 = "zf_1"
    const val TYPE_ZF_NEW = "zf_new"
    const val TYPE_URP = "urp"
    const val TYPE_URP_NEW = "urp_new"
    const val TYPE_QZ = "qz"
    const val TYPE_QZ_OLD = "qz_old"
    const val TYPE_QZ_CRAZY = "qz_crazy"
    const val TYPE_QZ_BR = "qz_br"
    const val TYPE_QZ_WITH_NODE = "qz_with_node"
    const val TYPE_CF = "cf"
    const val TYPE_PKU = "pku"
    const val TYPE_BNUZ = "bnuz"
    const val TYPE_LOGIN = "login"
    const val TYPE_MAINTAIN = "maintain"

    /**
     * 协议显示名（用于 UI 提示）
     */
    fun displayName(type: String?): String = when (type) {
        TYPE_QZ, TYPE_QZ_OLD, TYPE_QZ_CRAZY, TYPE_QZ_BR, TYPE_QZ_WITH_NODE -> "强智教务"
        TYPE_ZF, TYPE_ZF_1, TYPE_ZF_NEW -> "正方教务"
        TYPE_URP, TYPE_URP_NEW -> "URP 教务"
        TYPE_CF -> "青果教务"
        TYPE_PKU -> "北京大学"
        TYPE_BNUZ -> "北师珠"
        TYPE_LOGIN -> "特殊登录（v1 暂不支持）"
        TYPE_HELP -> "如何选择教务类型"
        TYPE_MAINTAIN -> "维护中"
        else -> type ?: ""
    }

    /**
     * 协议大类，用于 WebViewLogin UI 上的提示文案分类
     */
    fun category(type: String?): String = when (type) {
        TYPE_QZ, TYPE_QZ_OLD, TYPE_QZ_CRAZY, TYPE_QZ_BR, TYPE_QZ_WITH_NODE -> "qz"
        TYPE_ZF, TYPE_ZF_1, TYPE_ZF_NEW -> "zf"
        TYPE_URP, TYPE_URP_NEW -> "urp"
        else -> "other"
    }
}
