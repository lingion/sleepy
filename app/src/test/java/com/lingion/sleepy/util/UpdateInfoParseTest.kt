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

    // ─── 下载地址镜像改写(2026-09-05 用户令: api.github.com 可达 ≠ github.com 资产可达) ──

    private val githubAssetBody = """{"tag_name":"v1.0.47","body":"x","assets":[
        {"name":"app-arm64-v8a-release.apk","browser_download_url":"https://github.com/lingion/sleepy/releases/download/v1.0.47/app-arm64-v8a-release.apk"}]}"""

    @Test
    fun github_asset_url_is_rewritten_to_mirror() {
        // 用户实测: api.github.com 通(0.6s)但 github.com 资产 15s 超时 —
        // 下载地址必须改写到镜像, 镜像目录结构与 GitHub 1:1
        val info = parseReleaseJson(githubAssetBody, "1.0.46", "arm64-v8a")
        assertEquals(
            "https://gh.qdp.qzz.io/lingion/sleepy/releases/download/v1.0.47/app-arm64-v8a-release.apk",
            info.downloadUrl
        )
    }

    @Test
    fun non_github_asset_url_passes_through_untouched() {
        val foreign = """{"tag_name":"v1.0.47","body":"x","assets":[
            {"name":"app-arm64-v8a-release.apk","browser_download_url":"https://example.com/a.apk"}]}"""
        val info = parseReleaseJson(foreign, "1.0.46", "arm64-v8a")
        assertEquals("https://example.com/a.apk", info.downloadUrl)
    }

    @Test
    fun github_direct_url_is_derived_from_mirror_url_for_fallback() {
        // 下载失败时的回退对: 镜像地址 → 原始 GitHub 直连地址
        val mirror = "https://gh.qdp.qzz.io/lingion/sleepy/releases/download/v1.0.47/app-arm64-v8a-release.apk"
        val direct = "https://github.com/lingion/sleepy/releases/download/v1.0.47/app-arm64-v8a-release.apk"
        assertEquals(direct, toDirectGithubUrl(mirror))
        // 已是直连则原样返回(幂等)
        assertEquals(direct, toDirectGithubUrl(direct))
        // 非下载路径不改写
        assertEquals("https://gh.qdp.qzz.io/other/page", toDirectGithubUrl("https://gh.qdp.qzz.io/other/page"))
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
