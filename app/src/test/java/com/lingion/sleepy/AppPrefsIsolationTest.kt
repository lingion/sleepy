package com.lingion.sleepy

import com.lingion.sleepy.util.AppPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM isolation test for AppPrefs keys (TS-2 degraded from Robolectric).
 *
 * Verifies the literal-level guarantees that AppPrefs.kt:
 *   1. KEY_WIDGET_COLORLESS and KEY_COURSE_COLORLESS are different strings
 *   2. getter/setter functions reference no foreign constant (audit via inspection
 *      — done by separate grep gate in perBatchVerify)
 *
 * Behavioral isolation (writing one key not affecting the other) is verified
 * by source inspection + the separate grep gate that proves no cross-key
 * boolean copy pattern exists anywhere. The pure-JVM path here proves the *type* contract: both
 * keys exist as independent constants and have distinct string values.
 *
 * Phase1 semantics: this test does NOT need a Context — the literals are
 * compile-time `const val`s, pure JVM verifiable.
 */
class AppPrefsIsolationTest {

    @Test
    fun widget_and_course_colorless_keys_are_different_literals() {
        assertNotEquals(
            "B 键必须与 A 键字面量不同(零互读前置条件)",
            AppPrefs.KEY_WIDGET_COLORLESS,
            AppPrefs.KEY_COURSE_COLORLESS
        )
    }

    @Test
    fun course_colorless_key_value_is_exact_string() {
        assertTrue(
            "B 键字面量必须固定为 course_colorless(便于跨版本稳定)",
            AppPrefs.KEY_COURSE_COLORLESS == "course_colorless"
        )
    }

    @Test
    fun widget_colorless_key_value_is_unchanged() {
        // 旁证:A 键字面量未被本批误伤
        assertTrue(
            "A 键字面量必须保持 widget_colorless",
            AppPrefs.KEY_WIDGET_COLORLESS == "widget_colorless"
        )
    }

    @Test
    fun both_keys_reachable_statically() {
        // 旁证:两常量均可被引用,无 missing-field 编译错误
        val a: String = AppPrefs.KEY_WIDGET_COLORLESS
        val b: String = AppPrefs.KEY_COURSE_COLORLESS
        assertTrue(a.isNotEmpty())
        assertTrue(b.isNotEmpty())
        assertFalse("纯 JVM 编译断言占位:保证非空且不可合并", a == b)
    }
}