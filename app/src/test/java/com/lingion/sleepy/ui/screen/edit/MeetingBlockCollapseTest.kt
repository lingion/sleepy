package com.lingion.sleepy.ui.screen.edit

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingBlockCollapseTest {

    private fun draft() = MeetingBlockDraft(
        id = 1,
        days = mutableStateListOf(1),
        startNode = 1,
        step = 2,
        startTime = "08:00",
        endTime = "09:40"
    )

    private fun draftExpanded() = MeetingBlockDraft(
        id = 1,
        days = mutableStateListOf(1),
        startNode = 1,
        step = 2,
        startTime = "08:00",
        endTime = "09:40",
        expanded = true
    )

    @Test
    fun block_collapsed_by_default() {
        assertFalse("时段卡必须默认折叠 — 进编辑页先全收起", draft().expanded)
    }

    @Test
    fun expanded_constructor_param_propagates() {
        assertTrue("传 expanded=true 的卡必须开局展开", draftExpanded().expanded)
    }
}
