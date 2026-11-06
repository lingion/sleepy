package com.lingion.sleepy.data.jw

/**
 * jw_home 登记型解析器 — 恒空、恒零置信。
 *
 * 存在的唯一目的: 让 JwParserRegistry.supports("jw_home") 为真, 使目录里
 * "URL 可信但无服务端协议指纹"的 unverified 行过 JwSchoolInfo.canImport 门,
 * 用户点击后正常进入 WebView 登录通路。真实解析发生在登录后:
 * JwImportViewModel.parseHtml 对 TYPE_JW_HOME 按空白协议处理 (自动检测已收录协议),
 * 本类因此几乎不会被喂到课表页; 即便被直接构造, 也只返回空列表, 不抛、不假阳性。
 *
 * 不进 JwParserRegistry.TYPE_PRIORITY → 不进 run-all 候选 (allCandidates 以
 * TYPE_PRIORITY.keys 为源), 对 selectBest / adjudicate 零影响。
 */
class JwHomeParser(source: String) : JwParser(source) {

    override fun generateCourseList(): List<JwCourse> = emptyList()

    override fun confidence(): Int = 0

    override fun matchedFeatures(): List<String> = emptyList()
}
