package com.lingion.sleepy.util

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Read the release file itself so documentation changes exercise the real consumers. */
object ReleaseNotesCandidate {
    val markdown: String by lazy {
        sequenceOf(File("docs"), File("../docs"))
            .first { it.isDirectory }
            .listFiles()!!
            .filter { it.name.matches(Regex("release-notes-v\\d+\\.\\d+\\.\\d+\\.md")) }
            .maxBy { file ->
                file.name.removePrefix("release-notes-v").removeSuffix(".md")
                    .split('.').joinToString(".") { it.padStart(6, '0') }
            }
            .readText()
    }
    val version: String get() = Regex("(?m)^# Sleepy v(\\d+\\.\\d+\\.\\d+)$")
        .find(markdown)!!.groupValues[1]

    fun info(abi: String = "arm64-v8a"): UpdateInfo {
        val assets = JSONArray()
        listOf("arm64-v8a", "armeabi-v7a", "x86_64", "universal").forEach { arch ->
            assets.put(JSONObject().put("name", "app-$arch-release.apk")
                .put("browser_download_url", "https://github.com/lingion/sleepy/releases/download/v$version/app-$arch-release.apk"))
        }
        return parseReleaseJson(JSONObject().put("tag_name", "v$version")
            .put("body", markdown).put("assets", assets).toString(), "1.0.66", abi)
    }

    fun visible(text: String): String = MarkdownBlocks.parseInline(text).joinToString("") {
        when (it) {
            is MarkdownBlocks.Inline.Text -> it.text
            is MarkdownBlocks.Inline.Bold -> it.text
            is MarkdownBlocks.Inline.Code -> it.text
            is MarkdownBlocks.Inline.Link -> it.text
        }
    }
}
