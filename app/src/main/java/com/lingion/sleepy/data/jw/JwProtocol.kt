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
     * 重庆大学自建统一门户 my.cqu.edu.cn（REST API + Bearer token，非 HTML 解析）。
     * WebView 登录统一身份认证（2026-06 起需动态验证码双因素，人工登录不受影响）后，
     * 从 localStorage 取 cqu_edu_ACCESS_TOKEN，fetch 四个接口：
     *   GET  /api/resourceapi/session/info-detail          当前学期
     *   GET  /api/resourceapi/session/info/{termId}        学期起始日
     *   GET  /api/workspace/time-pattern/session-time-pattern  节次时间
     *   POST /api/timetable/class/timetable/student/my-table-detail?sessionId=…  课表
     * 外部佐证：时光课程表 cqu.js 适配器（茵符草）、321CQU/pymycqu。
     */
    const val TYPE_CQU = "cqu"

    /**
     * 湖南科大教务（正方青春版/强智混合自建，kdjw.hnust.cn / xxjw.hnust.cn）。
     * schools.json 已有 3 所 type="hnust" 的学校；T3 移植 HNUSTParser，T6 先补常量
     * 使 displayName/category 不落入 else 分支。
     */
    const val TYPE_HNUST = "hnust"

    /** T8 新加：upstream Common.kt 历史常量，暂未启用；T13 启用 */
    const val TYPE_HNIU = "hniu"

    /**
     * 合肥工业大学教务 (金智 EAMS5, eams5-student 系列, jxglstu.hfut.edu.cn)。
     * WebView 内 fetch 三段 (CAS→course-table→lessons→POST schedule-table/datum) 拿课表 JSON。
     * 上游协议形态: Chiu-xaH/HFUT-Schedule (MIT) 全链路参考。
     */
    const val TYPE_EAMS5 = "eams5"

    /**
     * 东南大学教务 (正方 URP 系, newxk.urp.seu.edu.cn, 用户粘 JSON 后 fetch 课表)。
     * JSON 字段集 {KCM,SKJS,JASMC,SKXQ,KSJC,JSJC,ZCMC,KCH,JXBQH} — 与 WakeupSchedule_BUPT
     * 强智系字段形态高度同构, 但走 JSON 而非 HTML; v1 单次 POST 拿全表, 无周次 bitmap 压缩。
     * 上游协议形态: sakimidare/SEUTimetable (Apache-2.0) TableParserUtils.kt parseWeekRange
     * 算法参考 (代码自写, 只复用逻辑)。
     */
    const val TYPE_SEU = "seu"

    /**
     * 浙江大学教务 (正方新版 zf_new, zdbk.zju.edu.cn, CAS = zjuam.zju.edu.cn RSA 加密登录)。
     * 字段集 {xkkh, xqj, dsz, djj, skcd, kcb, xxq} — kcb 字符串含 \\n 分隔的课名/周次串/老师/教室,
     * dsz="0"单/"1"双/"2"全周, djj=起始节, skcd=节次长度。
     * 上游协议形态: Xecades/zju-ical-py (LGPL-2.1) zjuam/ugrs.py + course/ugrs_course.py;
     * 字段映射参考, kcb 解析逻辑代码自写。
     */
    const val TYPE_ZJU = "zju"

    /**
     * 中国科学技术大学教务 (自研新版, jw.ustc.edu.cn, CAS = passport.ustc.edu.cn 图形验证码)。
     * JSON 路径: x.studentTableVm.activities[] — 字段 {courseName, room, teachers[], weeksStr,
     * weekday, startDate, endDate}。weeksStr "1-16"/"1-16单"/"1-16双" 一次性给完整范围 (EAMS5 优势项)。
     * 上游协议形态: 1970633640/USTC-timetable-to-ics (无 license) json_version.py — 算法参考。
     * CAS 验证码 → WebView session 必走。
     */
    const val TYPE_USTC = "ustc"

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
        TYPE_WISEDU, TYPE_CQU, TYPE_EAMS5, TYPE_PKU, TYPE_BNUZ, TYPE_CF, TYPE_HNUST, TYPE_HNIU,
        TYPE_SEU, TYPE_ZJU, TYPE_USTC,
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
        TYPE_CQU -> "重庆大学门户"
        TYPE_HNUST -> "湖南科大教务"
        TYPE_HNIU -> "湖南信息职业技术学院"
        TYPE_EAMS5 -> "合工大教务 (EAMS5)"
        TYPE_SEU -> "东南大学"
        TYPE_ZJU -> "浙江大学"
        TYPE_USTC -> "中国科学技术大学"
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
        TYPE_CQU -> "cqu"
        TYPE_EAMS5 -> "eams5"
        TYPE_SEU, TYPE_ZJU, TYPE_USTC -> "other"
        TYPE_HNUST, TYPE_HNIU -> "hnust"
        TYPE_CF -> "cf"
        TYPE_PKU, TYPE_BNUZ -> "other"
        else -> "other"
    }
}
