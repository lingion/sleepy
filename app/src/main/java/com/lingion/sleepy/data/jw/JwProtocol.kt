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

    /** 金智 Wisedu jwapp 微应用平台（JSON API 直连，非 HTML 解析）。如：哈尔滨工程大学 jwgl.hrbeu.edu.cn */
    const val TYPE_WISEDU = "wisedu"

    /**
     * 湖南科大教务（正方青春版/强智混合自建，kdjw.hnust.cn / xxjw.hnust.cn）。
     * schools.json 已有 3 所 type="hnust" 的学校；T3 移植 HNUSTParser，T6 先补常量
     * 使 displayName/category 不落入 else 分支。
     */
    const val TYPE_HNUST = "hnust"

    /** T8 新加：upstream Common.kt 历史常量，暂未启用；T13 启用 */
    const val TYPE_HNIU = "hniu"

    /**
     * T6 协议识别置信度（仅内部诊断，不进 UI）。
     *  HIGH = URL 唯一锚点（jwapp/sys/、jwglxt、default2.aspx ...）
     *  PAGE_HIGH = HTML 页面级唯一锚点（zftal-ui-、__VIEWSTATE+Table1 ...）
     *  LOW = 弱锚点（仅 host 子串）
     */
    enum class DetectConfidence { HIGH, PAGE_HIGH, LOW }

    /**
     * T8 新增：所有协议族常量的有序列表（用于 Registry 兜底遍历顺序）。
     * 顺序按 TYPE_PRIORITY 优先级：wisedu > pku > bnuz > cf > hnust > hniu >
     *                            zf > zf_1 > urp > urp_new > zf_new >
     *                            qz > qz_crazy > qz_br > qz_with_node > qz_old
     */
    val ALL_TYPES: List<String> = listOf(
        TYPE_WISEDU, TYPE_PKU, TYPE_BNUZ, TYPE_CF, TYPE_HNUST, TYPE_HNIU,
        TYPE_ZF, TYPE_ZF_1, TYPE_URP, TYPE_URP_NEW, TYPE_ZF_NEW,
        TYPE_QZ, TYPE_QZ_CRAZY, TYPE_QZ_BR, TYPE_QZ_WITH_NODE, TYPE_QZ_OLD,
    )

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
        TYPE_WISEDU -> "金智教务（直连）"
        TYPE_HNUST -> "湖南科大教务"
        TYPE_HNIU -> "湖南信息职业技术学院"
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
        TYPE_WISEDU -> "wisedu"
        TYPE_HNUST, TYPE_HNIU -> "hnust"
        TYPE_CF -> "cf"
        TYPE_PKU, TYPE_BNUZ -> "other"
        else -> "other"
    }
}
