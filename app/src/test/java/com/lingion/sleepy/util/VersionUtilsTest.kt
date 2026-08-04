package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionUtilsTest {
    @Test fun comparesNumericVersionsAndIgnoresBuildSuffix() {
        assertEquals(0, VersionUtils.compare("1.0.25-debug", "1.0.25"))
        assertEquals(0, VersionUtils.compare("v1.0.26", "1.0.26"))
        assert(VersionUtils.compare("1.0.25", "1.0.26") < 0)
        assert(VersionUtils.compare("1.0.10", "1.0.9") > 0)
    }
}
