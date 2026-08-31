package com.lingion.sleepy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackComposerTest {
    private val diagnostic = FeedbackComposer.Diagnostic(
        versionName = "1.0.42",
        versionCode = 42,
        androidVersion = "14",
        brand = "Google",
        model = "Pixel 7",
        resolution = "1080x2400",
        locale = "zh-CN",
        isDebug = true,
    )

    @Test
    fun githubUrl_encodesTitleBodyAndAppendsDiagnostics() {
        val result = FeedbackComposer.githubIssueUrl("Bug: 中文 & 符号", "描述\n含 & 符号", diagnostic)
        assertTrue(result.startsWith("https://github.com/lingion/sleepy/issues/new?"))
        assertTrue(result.contains("title="))
        assertTrue(result.contains("body="))
        assertFalse(result.contains(" "))
        // The diagnostic block (separately verified) ends up inside body=
        assertTrue(result.contains("VersionCode%3A") || result.contains("VersionCode%3a"))
        assertTrue(result.contains("Android%3A") || result.contains("Android%3a"))
    }

    @Test
    fun githubUrl_includesTemplateBeforeTitle() {
        val result = FeedbackComposer.githubIssueUrl("Test", "Desc", diagnostic, "bug_report")
        assertTrue(result.indexOf("template=") < result.indexOf("title="))
    }

    @Test
    fun mailtoUri_usesConfirmedAddressAndEncodesFields() {
        val result = FeedbackComposer.mailtoUri("中文标题", "内容\n和&符号", diagnostic)
        assertTrue(result.startsWith("mailto:lingion@hrbeu.edu.cn?subject="))
        assertTrue(result.contains("body="))
        assertFalse(result.contains(" "))
        assertTrue(result.contains("VersionCode%3A") || result.contains("VersionCode%3a"))
    }

    @Test
    fun diagnosticBlock_containsAllFieldsAndBuildType() {
        val block = FeedbackComposer.formatDiagnostic(diagnostic)
        assertTrue(block.contains("**Version:** 1.0.42"))
        assertTrue(block.contains("**VersionCode:** 42"))
        assertTrue(block.contains("**Android:** 14"))
        assertTrue(block.contains("**Device:** Google Pixel 7"))
        assertTrue(block.contains("**Resolution:** 1080x2400"))
        assertTrue(block.contains("**Locale:** zh-CN"))
        assertTrue(block.contains("**Build:** Debug"))
    }

    @Test
    fun diagnosticBlock_releaseBuild_isMarkedRelease() {
        val block = FeedbackComposer.formatDiagnostic(diagnostic.copy(isDebug = false))
        assertTrue(block.contains("**Build:** Release"))
        assertFalse(block.contains("**Build:** Debug"))
    }

    @Test
    fun diagnosticBlock_escapesMarkdownCharacters() {
        val block = FeedbackComposer.formatDiagnostic(diagnostic.copy(versionName = "**injected**"))
        assertTrue(block.contains("\\*\\*injected\\*\\*"))
    }

    @Test
    fun urls_haveNoTrailingAmpersand() {
        assertFalse(FeedbackComposer.githubIssueUrl("Test", "Desc", diagnostic).endsWith("&"))
        assertFalse(FeedbackComposer.mailtoUri("Test", "Desc", diagnostic).endsWith("&"))
    }
}
