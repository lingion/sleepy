package com.lingion.sleepy.data.jw

/**
 * 强智 qz_br 变体解析器。
 *
 * 与 [JwQzParser] 的唯一差异：单元格内课名行后跟 `<br>` 而非 `<font>`，
 * 因此课名提取必须用 `substringBefore("<br>")` 而非 `substringBefore("<font")`。
 *
 * 样本学校（上游 WakeupSchedule_BUPT SchoolListActivity.kt 实证）：
 *   - 北京林业大学 newjwxt.bjfu.edu.cn
 *   - 长春大学 cdjwc.ccu.edu.cn/jsxsd
 *   - 长沙理工 xk.csust.edu.cn
 *   - 广东金融 jwxt.gduf.edu.cn
 *   - 江西农大南昌商学院 223.83.249.67:8080/jsxsd
 *
 * 上游源码：dIT8Zv/WakeupSchedule_BUPT QzBrParser.kt (Apache-2.0)
 *   https://github.com/dIT8Zv/WakeupSchedule_BUPT/blob/master/app/src/main/java/com/suda/yzune/wakeupschedule/schedule_import/parser/qz/QzBrParser.kt
 */
class JwQzBrParser(source: String) : JwQzParser(source) {

    /**
     * 课名提取：用课名后第一个 `<br>` 截断，不再走 `<font` 路径。
     * 上游原始实现见 QzBrParser.parseCourseName。
     *
     * 注意：上游没有对 substringBefore 缺失 `<br>` 做兜底，强行走 Jsoup 解析会
     * 把整段 HTML 当课名；我们保留上游忠实行为，依赖 `generateCourseList`
     * 入口处的 `courseElements.html()` 必然含 `<br>`（强智单元格结构）。
     */
    override fun parseCourseName(infoStr: String): String {
        return infoStr.substringBefore("<br>").trim()
    }

    /** T8 §2.5: 继承 kbtable 锚点 + br-not-font 特征 */
    override fun matchedFeatures(): List<String> =
        super.matchedFeatures() + listOf("br-not-font")
}
