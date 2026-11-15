package com.lingion.sleepy.ui.nav

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationEntrySnapshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun outgoingPageKeepsItsVisibleListUntilItBecomesActiveAgain() {
        val active = mutableStateOf(true)
        val liveRows = mutableStateOf(listOf("old"))
        compose.setContent {
            CompositionLocalProvider(LocalNavigationEntryActive provides active.value) {
                val visibleRows = rememberNavigationEntrySnapshot(liveRows.value)
                BasicText(visibleRows.joinToString(","), Modifier.testTag("rows"))
            }
        }
        compose.onNodeWithTag("rows").assertTextEquals("old")

        // Navigation and Room may update in the same frame. The outgoing page must
        // retain the last value it actually displayed, not the newly inserted row.
        compose.runOnIdle {
            active.value = false
            liveRows.value = listOf("old", "new")
        }
        compose.onNodeWithTag("rows").assertTextEquals("old")
        compose.runOnIdle { liveRows.value = listOf("old", "new", "latest") }
        compose.onNodeWithTag("rows").assertTextEquals("old")

        compose.runOnIdle { active.value = true }
        compose.onNodeWithTag("rows").assertTextEquals("old,new,latest")
        compose.runOnIdle { liveRows.value = listOf("latest") }
        compose.onNodeWithTag("rows").assertTextEquals("latest")
    }
}
