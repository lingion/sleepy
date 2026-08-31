package com.lingion.sleepy.util

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateInfoParseTest {

    private val sampleBody = """{"tag_name":"v1.0.32","body":"## v1.0.32\n\n修复 bug","assets":[
        {"name":"app-arm64-v8a-release.apk","browser_download_url":"https://example.com/a.apk"},
        {"name":"app-armeabi-v7a-release.apk","browser_download_url":"https://example.com/b.apk"}]}"""

    @Test
    fun parses_version_changelog_url_from_github_json() {
        val info = parseReleaseJson(sampleBody, "1.0.31", "arm64-v8a")
        assertEquals("1.0.32", info.version)
        assertEquals("## v1.0.32\n\n修复 bug", info.changelog)
        assertEquals("https://example.com/a.apk", info.downloadUrl)
        assertTrue(info.isUpdateAvailable)
    }

    @Test
    fun older_remote_version_is_not_an_update() {
        val info = parseReleaseJson(sampleBody, "1.0.33", "arm64-v8a")
        assertFalse(info.isUpdateAvailable)
    }

    @Test
    fun same_version_with_force_flag_is_update() {
        val body = sampleBody.replace("修复 bug", "修复 bug SLEEPY_FORCE_UPDATE=true")
        val info = parseReleaseJson(body, "1.0.32", "arm64-v8a")
        assertTrue(info.isUpdateAvailable)
    }

    @Test
    fun picks_correct_abi_asset() {
        val info = parseReleaseJson(sampleBody, "1.0.31", "armeabi-v7a")
        assertEquals("https://example.com/b.apk", info.downloadUrl)
    }

    @Test
    fun missing_asset_for_abi_returns_blank_url() {
        val noX86 = """{"tag_name":"v2.0.0","body":"x","assets":[
            {"name":"app-arm64-v8a-release.apk","browser_download_url":"https://example.com/a.apk"}]}"""
        val info = parseReleaseJson(noX86, "1.0.0", "x86_64")
        assertEquals("", info.downloadUrl)
    }

    // ─── 镜像页 changelog 提取 ───────────────────────────────────────────

    private val mirrorPage = """
        <html><head><title>Release v1.0.39</title></head><body>
        <div data-pjax="true" data-test-selector="body-content" data-view-component="true" class="markdown-body tmp-my-3"><h2>v1.0.39</h2>
        <p>Two changes: bug reported in <a class="issue-link" href="https://github.com/lingion/sleepy/issues/5">#5</a> is fixed.</p>
        <h3>New</h3>
        <p><strong>Each time slot keeps its own week range</strong></p>
        <ul>
        <li>Every time slot carries its own start week.</li>
        <li>Slots that share a day stay separate.</li>
        </ul>
        <h3>Fixes</h3>
        <ul>
        <li><strong>Weekday header wrong date</strong> is fixed.</li>
        </ul>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun mirrorPage_extracts_markdown_body_block() {
        val md = parseMirrorPage(mirrorPage, "v1.0.39")
        assertTrue(md.contains("Each time slot keeps its own week range"))
        assertTrue(md.contains("start week"))
    }

    @Test
    fun mirrorPage_converts_headings_lists_bold_links() {
        val md = parseMirrorPage(mirrorPage, "v1.0.39")
        assertTrue(md.contains("## v1.0.39"))
        assertTrue(md.contains("### New"))
        assertTrue(md.contains("### Fixes"))
        assertTrue(md.contains("- Every time slot carries its own start week."))
        assertTrue(md.contains("**Each time slot keeps its own week range**"))
        assertTrue(md.contains("[#5](https://github.com/lingion/sleepy/issues/5)"))
    }

    @Test
    fun mirrorPage_strips_unsafe_tags_and_keeps_text() {
        val md = parseMirrorPage(mirrorPage, "v1.0.39")
        assertFalse(md.contains("<div"))
        assertFalse(md.contains("class="))
        assertFalse(md.contains("data-pjax"))
    }

    @Test
    fun mirrorPage_no_markdown_body_returns_empty() {
        assertEquals("", parseMirrorPage("<html><body>404</body></html>", "v1.0.39"))
    }

    @Test
    fun mirrorPage_multiple_markdown_bodies_uses_first() {
        val two = mirrorPage.replace(
            "</body>", """
            <div class="markdown-body"><h2>comment</h2></div></body>
        """.trimIndent()
        )
        val md = parseMirrorPage(two, "v1.0.39")
        assertTrue(md.contains("Each time slot keeps its own week range"))
        assertFalse(md.contains("comment"))
    }

    @Test
    fun mirrorPage_handles_multiline_div_without_regex_greed() {
        // .*? 非贪婪在多 markdown-body 时必须停在第一个闭合 div,而不是吞掉后半页
        val md = parseMirrorPage(mirrorPage, "v1.0.39")
        assertFalse(md.contains("</body>"))
        assertFalse(md.contains("</html>"))
    }
}
