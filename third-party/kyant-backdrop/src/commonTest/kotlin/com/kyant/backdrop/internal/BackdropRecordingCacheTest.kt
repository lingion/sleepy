package com.kyant.backdrop.internal

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackdropRecordingCacheTest {
    private val size = IntSize(1000, 2000)

    @Test fun staticSourceReusesOnlyCompletedRecording() {
        val cache = BackdropRecordingCache()
        repeat(2) { assertTrue(cache.needsRecord("image", size, 3f, 1f, LayoutDirection.Ltr)) }
        cache.recorded("image", size, 3f, 1f, LayoutDirection.Ltr)
        repeat(120) { assertFalse(cache.needsRecord("image", size, 3f, 1f, LayoutDirection.Ltr)) }
    }

    @Test fun unkeyedAndTransitionFramesAlwaysRecordAndInvalidateOldFingerprint() {
        val cache = BackdropRecordingCache()
        cache.recorded("image", size, 3f, 1f, LayoutDirection.Ltr)
        repeat(3) {
            assertTrue(cache.needsRecord(null, size, 3f, 1f, LayoutDirection.Ltr))
            cache.recorded(null, size, 3f, 1f, LayoutDirection.Ltr)
        }
        assertTrue(cache.needsRecord("image", size, 3f, 1f, LayoutDirection.Ltr))
    }

    @Test fun sourceGeometryEnvironmentAndLifecycleInvalidateRecording() {
        val cache = BackdropRecordingCache()
        cache.recorded(listOf("image", 0.5f), size, 3f, 1f, LayoutDirection.Ltr)
        assertFalse(cache.needsRecord(listOf("image", 0.5f), size, 3f, 1f, LayoutDirection.Ltr))
        assertTrue(cache.needsRecord(listOf("image", 0.6f), size, 3f, 1f, LayoutDirection.Ltr))
        assertTrue(cache.needsRecord(listOf("new-image", 0.5f), size, 3f, 1f, LayoutDirection.Ltr))
        assertTrue(cache.needsRecord(listOf("image", 0.5f), IntSize(2000, 1000), 3f, 1f, LayoutDirection.Ltr))
        assertTrue(cache.needsRecord(listOf("image", 0.5f), size, 2f, 1f, LayoutDirection.Ltr))
        assertTrue(cache.needsRecord(listOf("image", 0.5f), size, 3f, 1.2f, LayoutDirection.Ltr))
        assertTrue(cache.needsRecord(listOf("image", 0.5f), size, 3f, 1f, LayoutDirection.Rtl))
        cache.clear()
        assertTrue(cache.needsRecord(listOf("image", 0.5f), size, 3f, 1f, LayoutDirection.Ltr))
    }
}
