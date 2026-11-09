package com.lingion.sleepy.data.jw

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UcasDetailFetchIntegrityTest {
    private fun grid(count: Int) = (1..count).joinToString("") { "<a href=\"https://xkcts.ucas.ac.cn:8443/course/coursetime/$it\">甲</a>" }
    private val detail = "<div class=\"mc-body\"><p>课程名称：甲</p><table>" +
        "<tr><th>上课时间</th><td>星期一：第1节。</td></tr><tr><th>上课地点</th><td>A</td></tr>" +
        "<tr><th>上课周次</th><td>1、3</td></tr></table></div>"

    @Test fun `UCAS all details must succeed before combined input is returned`() = runBlocking {
        val success = UcasDetailFetch.enrichWith(grid(2)) { detail }
        assertEquals(2, JwUcasParser.detailSections(success).size)
        val failed = runCatching { UcasDetailFetch.enrichWith(grid(2)) { if (it.endsWith("/2")) throw IOException("offline") else detail } }
        assertTrue("Partial collection must fail", failed.exceptionOrNull() is IOException)
    }

    @Test fun `UCAS login response and empty detail are failures even when HTTP succeeded`() = runBlocking {
        for (bad in listOf("<html><form>登录</form></html>", "<div class=\"mc-body\"><p>课程名称：甲</p></div>")) {
            assertTrue(runCatching { UcasDetailFetch.enrichWith(grid(1)) { bad } }.exceptionOrNull() is IOException)
        }
    }

    @Test fun `UCAS detail limit cannot silently truncate the schedule`() = runBlocking {
        var requests = 0
        assertTrue(runCatching { UcasDetailFetch.enrichWith(grid(51)) { requests++; detail } }.exceptionOrNull() is IOException)
        assertEquals(0, requests)
    }

    @Test fun `UCAS cancellation propagates to stop remaining requests`() = runBlocking {
        var requests = 0
        val result = runCatching { UcasDetailFetch.enrichWith(grid(2)) { requests++; throw CancellationException("cancelled") } }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(1, requests)
    }
}
