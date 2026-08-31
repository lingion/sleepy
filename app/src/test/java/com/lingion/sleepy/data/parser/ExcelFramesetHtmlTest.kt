package com.lingion.sleepy.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Excel "另存为网页" 导出的 Frameset HTML 识别(issue #6)。
 *
 * 顶层文件不含真课表 <table>(只有 JS 生成标签栏的装饰 table),
 * 课程在 <link id="shLink" href="xxx.files/sheet001.html"> 引用的附属文件里。
 * Sleepy 通过 OpenDocument 只拿到单个 URI,读不到同目录附属文件 →
 * 必须识别这个结构并抛出带指引的错误,而不是模糊的"HTML 中未找到表格"。
 */
class ExcelFramesetHtmlTest {

    /** issue #6 真实结构的精简样本:Excel 15 frameset + GBK 声明 + shLink 引用 */
    private val framesetHtml = """
        <html xmlns:x="urn:schemas-microsoft-com:office:excel"
              xmlns="http://www.w3.org/TR/REC-html40">
        <head>
        <meta name="Excel Workbook Frameset">
        <meta http-equiv=Content-Type content="text/html; charset=gb2312">
        <meta name=ProgId content="Excel.Sheet">
        <meta name=Generator content="Microsoft Excel 15">
        <link id="shLink" href="大二课表学期1.files/sheet001.html">
        </head>
        <frameset rows="*,18">
        <frame src="大二课表学期1.files/sheet001.html" name="frSheet">
        </frameset>
        </html>
    """.trimIndent()

    @Test
    fun detectsExcelFrameset() {
        assertTrue(ScheduleParser.isExcelFrameset(framesetHtml))
    }

    @Test
    fun extractsSheetFileReference() {
        val ref = ScheduleParser.excelSheetRef(framesetHtml)
        assertEquals("大二课表学期1.files/sheet001.html", ref)
    }

    @Test
    fun normalHtmlIsNotFrameset() {
        val normal = """<html><body><table><tr><td>高数 1 1-2 1-16</td></tr></table></body></html>"""
        assertTrue(!ScheduleParser.isExcelFrameset(normal))
    }

    @Test
    fun framesetParseFailsWithGuidance() {
        val result = ScheduleParser.parse(framesetHtml, 0L)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        // 报错必须点名 Excel 结构和可行出路,不能用泛泛的"未找到表格"
        assertTrue("msg=$msg", msg.contains("Excel"))
        assertTrue("msg=$msg", msg.contains("CSV") || msg.contains("csv"))
    }

    @Test
    fun plainHtmlWithoutTableStillErrorsAsBefore() {
        // 非 frameset 的普通 HTML 无表格 → 保持原有报错语义
        val result = ScheduleParser.parse("""<html><body><p>hello</p></body></html>""", 0L)
        assertTrue(result.isFailure)
        assertTrue(!result.exceptionOrNull()?.message.orEmpty().contains("Excel"))
    }
}
