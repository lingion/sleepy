package com.lingion.sleepy.data.jw

import com.lingion.sleepy.ui.screen.imports.FrameCaptureStatus
import com.lingion.sleepy.ui.screen.imports.FrameSnapshot
import com.lingion.sleepy.ui.screen.imports.FrameSnapshotList
import com.lingion.sleepy.ui.screen.imports.FrameTraversalTree
import org.junit.Assert.assertEquals
import org.junit.Test

class JwZfRoundTwoCaptureTest {
    @Test fun `bare kbcx path reaches the existing ZF JSON collector`() {
        val school = JwSchoolInfo("G", "测试学校", "https://jw.example.edu.cn/kbcx/", JwProtocol.TYPE_ZF_NEW)
        assertEquals(FetchKind.ZF_NEW, JwFetchProtocol.pick(school,
            "https://jw.example.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151"))
    }

    @Test fun `QZ 2024 tooltip page enters the frame candidates`() {
        val html = """
            <table><tr><td>上午</td><td name='kbDataTd'><ul><li class='qz-toolitiplists'>
            <div class='qz-tooltipContent-title'>离散数学</div>
            <div class='qz-tooltipContent-detailitem'>老师：陈老师</div>
            <div class='qz-tooltipContent-detailitem'>地点：理科楼305</div>
            <div class='qz-tooltipContent-detailitem'>时间：1-4周[1-2节]</div>
            </li></ul></td></tr></table>
        """.trimIndent()
        val frames = FrameSnapshotList("https://jw.example.edu.cn/", 8,
            listOf(FrameSnapshot("(top)", null, 0, emptyList(), html, null)))
        assertEquals(FrameCaptureStatus.OK, FrameTraversalTree.selectBestFrame(frames).status)
    }

    @Test fun `modern empty application table cannot claim a timetable anchor`() {
        val html = "<table class='el-table__header'><tr><th><div>姓名</div></th></tr></table>" +
            "<table class='el-table__body'><tr><td><div class='cell' style='text-align: center;'>测试</div></td></tr></table>"
        assertEquals(emptyList<String>(), FrameTraversalTree.findAnchors(html))
    }

    @Test fun `class table six and nested table tb enter the frame candidates`() {
        for (id in listOf("Table6", "table_tb")) {
            val html = "<table id='$id'><tr><td>课表</td></tr></table>"
            assertEquals(true, FrameTraversalTree.findAnchors(html).isNotEmpty())
        }
    }
}
