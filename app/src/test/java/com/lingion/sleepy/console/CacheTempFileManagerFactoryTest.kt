package com.lingion.sleepy.console

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheTempFileManagerFactoryTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun clear_closesOpenStreamAndPreservesOtherRequestsFiles() {
        val factory = CacheTempFileManagerFactory(tempDir.root)
        val first = factory.create()
        val second = factory.create()
        val firstFile = first.createTempFile(null)
        val secondFile = second.createTempFile(null)
        try {
            firstFile.open().write("first".toByteArray())
            secondFile.open().write("second".toByteArray())

            first.clear()

            assertFalse("清理必须关闭句柄并删除本请求文件", File(firstFile.name).exists())
            assertTrue("其他请求的文件必须保留", File(secondFile.name).exists())
        } finally {
            first.clear()
            second.clear()
        }
        assertFalse(File(secondFile.name).exists())
    }
}
