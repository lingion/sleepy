package com.lingion.sleepy.testutil

import java.io.File

/** Locate repository files from either the Gradle root or module working directory. */
internal fun projectSourceFile(relativePath: String): File {
    val relative = relativePath.replace('/', File.separatorChar)
    var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile.normalize()
    while (directory != null) {
        val candidate = File(directory, relative)
        if (candidate.exists()) return candidate
        directory = directory.parentFile
    }
    error("Unable to locate project path '$relativePath' from ${System.getProperty("user.dir") ?: "."}")
}

internal fun readProjectSource(relativePath: String): String = projectSourceFile(relativePath).readText()
