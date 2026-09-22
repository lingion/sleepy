package com.lingion.sleepy.ui.screen.imports

import com.lingion.sleepy.R

/**
 * 排查全量包导出进度模型 — 2026-09-21 用户: 点导出后必须看到每个原子步骤的
 * 进度在哪、百分之多少, 禁止无止境"正在生成"黑箱等待。
 *
 * [DumpStage] 枚举顺序 = JwImportActivity.exportDiagnosticDump 管线的真实采集
 * 顺序(枚举序即 UI 展示序); [DiagDumpProgress.percent] 按 已完成步骤数/总步骤数
 * 计算, 单调不减、终态 100%、超 100 钳位。
 */
enum class DumpStage(val labelRes: Int) {
    /** 现抓 DOM 可点元素清单 (DOM_INVENTORY_JS) */
    DomInventory(R.string.jw_diag_stage_dom_inventory),
    /** Web Storage 全键值 (STORAGE_JS) */
    Storage(R.string.jw_diag_stage_storage),
    /** 页面链接 + select 下拉枚举 (LINKS_JS) */
    Links(R.string.jw_diag_stage_links),
    /** 运行时 fetch/XHR 快照 (DIAGNOSTIC_NETWORK_SNAPSHOT_JS) */
    NetworkSnapshot(R.string.jw_diag_stage_network_snapshot),
    /** 网络请求重放 (DIAGNOSTIC_NETWORK_EXPORT_JS, 30s 上限) */
    NetworkReplay(R.string.jw_diag_stage_network_replay),
    /** 同源资源重取 (RESOURCE_REPLAY_JS, 16s 上限) */
    ResourceReplay(R.string.jw_diag_stage_resource_replay),
    /** Cookie 全量值 (CookieManager) */
    Cookies(R.string.jw_diag_stage_cookies),
    /** zip 组装 (JwCaptureDump.buildZip) */
    ZipAssembly(R.string.jw_diag_stage_zip),
    /** 落盘 Download/Sleepy/教务日志 + 拉系统分享面板 */
    SaveShare(R.string.jw_diag_stage_save_share),
}

/**
 * 单一进度快照。[stepsDone] = 已完成的原子步骤数(0..DumpStage.entries.size)。
 * 纯数据 + 纯函数, JVM 单测直接跑。
 */
data class DiagDumpProgress(
    val stage: DumpStage,
    val stepsDone: Int,
    val totalCount: Int = DumpStage.entries.size,
) {
    val percent: Int
        get() = if (totalCount <= 0) 0 else (stepsDone * 100 / totalCount).coerceIn(0, 100)
}
