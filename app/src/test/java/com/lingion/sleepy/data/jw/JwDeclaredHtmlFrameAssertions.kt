package com.lingion.sleepy.data.jw

import com.lingion.sleepy.ui.screen.imports.FrameCaptureStatus
import com.lingion.sleepy.ui.screen.imports.FrameSnapshot
import com.lingion.sleepy.ui.screen.imports.FrameSnapshotList
import com.lingion.sleepy.ui.screen.imports.FrameTraversalTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals

/** Follow the same frame selection and declared parsing path used by the import screen. */
internal fun assertDeclaredHtmlFrame(type: String, html: String, expected: List<JwCourse>) {
    if (expected.isEmpty()) return
    val snapshots = FrameSnapshotList("https://school.example/schedule", 8, listOf(
        FrameSnapshot("(top)", null, 0, emptyList(), "<html><body>教务门户</body></html>", null),
        FrameSnapshot("schedule", "/schedule", 1, listOf("(top)"), html, null),
    ))
    val selected = FrameTraversalTree.selectBestFrame(snapshots, declaredType = type)
    assertEquals(type, FrameCaptureStatus.OK, selected.status)
    assertEquals(type, listOf("(top)", "schedule"), selected.selectedFramePath)
    assertEquals(type, expected, JwImportViewModel.parseHtmlStatic(selected.html, type).first)
    // A generic wrapper id cannot turn the same source into an unrelated declared school.
    if (FrameTraversalTree.findAnchors(html).isEmpty()) {
        assertNotEquals(type, FrameCaptureStatus.OK,
            FrameTraversalTree.selectBestFrame(snapshots, declaredType = "unknown").status)
        val login = snapshots.copy(frames = listOf(snapshots.frames[0].copy(
            outerHTML = """<form action="/login"><input name="username"><input type="password" placeholder="请输入密码"><button>登录</button></form>"""
        ), snapshots.frames[1]))
        assertEquals(type, FrameCaptureStatus.SESSION_EXPIRED,
            FrameTraversalTree.selectBestFrame(login, declaredType = type).status)
    }
}
