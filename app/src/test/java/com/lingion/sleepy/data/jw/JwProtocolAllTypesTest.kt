package com.lingion.sleepy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T8 — JwProtocol 常量完备性 + displayName/category 一致性。
 */
class JwProtocolAllTypesTest {

    @Test
    fun `TYPE_HNUST and TYPE_HNIU are defined`() {
        assertNotNull(JwProtocol.TYPE_HNUST)
        assertNotNull(JwProtocol.TYPE_HNIU)
        assertEquals("hnust", JwProtocol.TYPE_HNUST)
        assertEquals("hniu", JwProtocol.TYPE_HNIU)
    }

    @Test
    fun `ALL_TYPES contains hnust and hniu`() {
        assertTrue(JwProtocol.ALL_TYPES.contains(JwProtocol.TYPE_HNUST))
        assertTrue(JwProtocol.ALL_TYPES.contains(JwProtocol.TYPE_HNIU))
    }

    @Test
    fun `displayName for hnust and hniu is non-blank`() {
        assertTrue(JwProtocol.displayName(JwProtocol.TYPE_HNUST).isNotBlank())
        assertTrue(JwProtocol.displayName(JwProtocol.TYPE_HNIU).isNotBlank())
    }

    @Test
    fun `category for hnust and hniu is hnust`() {
        assertEquals("hnust", JwProtocol.category(JwProtocol.TYPE_HNUST))
        assertEquals("hnust", JwProtocol.category(JwProtocol.TYPE_HNIU))
    }

    @Test
    fun `ALL_TYPES has consistent displayName for every entry`() {
        for (t in JwProtocol.ALL_TYPES) {
            assertTrue("type $t displayName 必须非空", JwProtocol.displayName(t).isNotBlank())
            assertTrue(
                "type $t category 必须落到已知九档之一",
                JwProtocol.category(t) in setOf("qz", "zf", "urp", "wisedu", "cqu", "chaoxing", "eams5", "hnust", "cf", "other"),
            )
        }
    }
}
