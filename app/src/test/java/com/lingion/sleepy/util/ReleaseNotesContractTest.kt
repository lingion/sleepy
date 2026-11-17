package com.lingion.sleepy.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

class ReleaseNotesContractTest {
    @Test fun publishedBodyPreservesBothLanguagesAndSelectsEachApk() {
        listOf("arm64-v8a", "armeabi-v7a", "x86_64", "universal").forEach { abi ->
            val info = ReleaseNotesCandidate.info(abi)
            assertTrue(info.isUpdateAvailable)
            assertEquals(ReleaseNotesCandidate.version, info.version)
            assertEquals(ReleaseNotesCandidate.markdown, info.changelog)
            assertEquals("https://gh.qdp.qzz.io/lingion/sleepy/releases/download/v${info.version}/app-$abi-release.apk", info.downloadUrl)
        }
        val blocks = MarkdownBlocks.parse(ReleaseNotesCandidate.markdown)
        assertEquals(2, blocks.filterIsInstance<MarkdownBlocks.Block.Heading>().count { it.level == 1 })
        // Every supported nonblank source line must survive block and inline parsing.
        val rendered = blocks.flatMap { block -> when (block) {
            is MarkdownBlocks.Block.Heading -> listOf(block.text)
            is MarkdownBlocks.Block.Bullet -> block.items
            is MarkdownBlocks.Block.Paragraph -> listOf(block.text)
        } }.map(ReleaseNotesCandidate::visible)
        val expected = ReleaseNotesCandidate.markdown.lineSequence().filter { it.isNotBlank() }
            .map { it.replace(Regex("^(#{1,6}|-)\\s+"), "") }
            .map(ReleaseNotesCandidate::visible).toList()
        assertEquals(expected, rendered)
    }

    @Test fun websiteHtmlAlsoSurvivesTheMirrorFallback() {
        // The cross-consumer CI job supplies HTML from sleepy-site's actual marked dependency.
        val fixture = System.getenv("SLEEPY_RELEASE_MIRROR_FIXTURE")
        assumeNotNull(fixture)
        val mirrored = parseMirrorPage(File(requireNotNull(fixture)).readText(), "v${ReleaseNotesCandidate.version}")
        // HTML line breaks can split one list into adjacent Bullet blocks. Compare
        // ordered items with their types/inline formatting, not that grouping detail.
        fun items(markdown: String) = MarkdownBlocks.parse(markdown).flatMap { block ->
            if (block is MarkdownBlocks.Block.Bullet) block.items.map { MarkdownBlocks.Block.Bullet(listOf(it)) }
            else listOf(block)
        }
        assertEquals(items(ReleaseNotesCandidate.markdown), items(mirrored))
    }
}
