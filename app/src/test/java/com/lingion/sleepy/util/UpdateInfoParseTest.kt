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
}
