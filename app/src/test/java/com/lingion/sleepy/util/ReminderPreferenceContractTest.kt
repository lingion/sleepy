package com.lingion.sleepy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReminderPreferenceContractTest {
    private data class PreferenceContract(
        val getter: String,
        val setter: String,
        val key: String,
        val default: String,
    )

    private fun readAppPrefsSource(): String =
        File(
            System.getProperty("sleepy.test.root") ?: ".",
            "app/src/main/java/com/lingion/sleepy/util/AppPrefs.kt",
        ).readText()

    private fun extractFunctionBody(source: String, functionName: String): String {
        val declarationStart = source.indexOf("fun $functionName(")
        check(declarationStart >= 0) { "Missing function: $functionName" }
        val braceStart = source.indexOf('{', declarationStart)
        val equalsStart = source.indexOf('=', declarationStart)

        if (braceStart >= 0 && (equalsStart < 0 || braceStart < equalsStart)) {
            var depth = 0
            for (index in braceStart until source.length) {
                when (source[index]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return source.substring(braceStart + 1, index)
                    }
                }
            }
            error("Unbalanced braces in: $functionName")
        }

        check(equalsStart >= 0) { "Missing expression body: $functionName" }
        val expressionStart = (equalsStart + 1 until source.length)
            .firstOrNull { !source[it].isWhitespace() } ?: source.length
        val expressionEnd = source.indexOf('\n', expressionStart).takeIf { it >= 0 } ?: source.length
        return source.substring(expressionStart, expressionEnd)
    }

    private fun normalized(body: String): String = body.replace(Regex("\\s+"), " ").trim()

    @Test
    fun `reminder date preferences are independent from grey holiday preferences`() {
        val source = readAppPrefsSource()
        assertTrue(source.contains("holiday_reminder_rules_enabled"))
        assertTrue(source.contains("holiday_reminder_public_holiday"))
        assertTrue(source.contains("holiday_reminder_transfer_holiday"))
        assertTrue(source.contains("holiday_reminder_makeup_workday"))
        assertTrue(source.contains("holiday_reminder_ordinary_weekend"))
        assertFalse(source.contains("isHolidayGreyHoliday(ctx)"))
    }

    @Test
    fun `each getter uses its own key and default`() {
        val source = readAppPrefsSource()
        contracts().forEach { contract ->
            assertEquals(
                "sp(ctx).getBoolean(${contract.key}, ${contract.default})",
                normalized(extractFunctionBody(source, contract.getter)),
            )
        }
    }

    @Test
    fun `each setter body contains only its matching persistent write`() {
        val source = readAppPrefsSource()
        contracts().forEach { contract ->
            assertEquals(
                "sp(ctx).edit().putBoolean(${contract.key}, v).apply()",
                normalized(extractFunctionBody(source, contract.setter)),
            )
        }
    }

    @Test
    fun `master setter cannot reset child preferences`() {
        val source = readAppPrefsSource()
        val masterBody = normalized(
            extractFunctionBody(source, "setHolidayReminderRulesEnabled"),
        )
        assertEquals(
            "sp(ctx).edit().putBoolean(KEY_HOLIDAY_REMINDER_RULES_ENABLED, v).apply()",
            masterBody,
        )
        contracts().drop(1).forEach { contract ->
            assertFalse(masterBody.contains(contract.key))
        }
    }

    private fun contracts(): List<PreferenceContract> = listOf(
        PreferenceContract(
            getter = "isHolidayReminderRulesEnabled",
            setter = "setHolidayReminderRulesEnabled",
            key = "KEY_HOLIDAY_REMINDER_RULES_ENABLED",
            default = "false",
        ),
        PreferenceContract(
            getter = "isHolidayReminderPublicHolidayEnabled",
            setter = "setHolidayReminderPublicHolidayEnabled",
            key = "KEY_HOLIDAY_REMINDER_PUBLIC_HOLIDAY",
            default = "true",
        ),
        PreferenceContract(
            getter = "isHolidayReminderTransferHolidayEnabled",
            setter = "setHolidayReminderTransferHolidayEnabled",
            key = "KEY_HOLIDAY_REMINDER_TRANSFER_HOLIDAY",
            default = "true",
        ),
        PreferenceContract(
            getter = "isHolidayReminderMakeupWorkdayEnabled",
            setter = "setHolidayReminderMakeupWorkdayEnabled",
            key = "KEY_HOLIDAY_REMINDER_MAKEUP_WORKDAY",
            default = "true",
        ),
        PreferenceContract(
            getter = "isHolidayReminderOrdinaryWeekendEnabled",
            setter = "setHolidayReminderOrdinaryWeekendEnabled",
            key = "KEY_HOLIDAY_REMINDER_ORDINARY_WEEKEND",
            default = "true",
        ),
    )
}
