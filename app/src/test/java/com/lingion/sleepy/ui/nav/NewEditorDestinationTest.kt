package com.lingion.sleepy.ui.nav

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.R
import com.lingion.sleepy.ui.theme.LocalUiStyle
import com.lingion.sleepy.ui.theme.UiStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-w360dp-h640dp-mdpi")
class NewEditorDestinationTest {
    @get:Rule val compose = createComposeRule()

    // This scope stands in for the retained ViewModel, independently of the
    // destination's composition. All deferred completions run on the test UI thread.
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun cleanup() {
        modelScope.cancel()
    }

    @Test
    fun initializingToolbarBackWorksBeforeTheRecordIsReady() {
        val inserted = CompletableDeferred<Long>()
        val deleted = mutableListOf<Long>()
        var insertCalls = 0
        var backCalls = 0
        var closedCalls = 0
        val creation = EditorCreation(
            scope = modelScope,
            create = { insertCalls++; inserted.await() },
            discard = { deleted += it },
        )
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiStyle provides UiStyle.Classic) {
                    NewEditorDestination(
                        requestId = "new-table",
                        title = R.string.edit_table_title,
                        creationFor = { creation },
                        onDiscard = {
                            assertSame(creation, it)
                            backCalls++
                            it.discardAndClose { closedCalls++ }
                        },
                        onSaved = { error("An unsaved editor must not finish saving") },
                    ) { id -> Text("Editor $id", Modifier.testTag("editor")) }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("editor").assertDoesNotExist()
        val backLabel = ApplicationProvider.getApplicationContext<Context>().getString(R.string.back)
        compose.onNodeWithContentDescription(backLabel).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, insertCalls)
            assertEquals(1, backCalls)
            assertEquals(0, closedCalls)
            assertTrue(deleted.isEmpty())
            inserted.complete(42L)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf(42L), deleted)
            assertEquals(1, closedCalls)
        }
    }

    @Test
    fun saveCompletedWhileInactiveReturnsOnlyWhenActiveAndOnlyOnce() {
        val active = mutableStateOf(true)
        val write = CompletableDeferred<Unit>()
        var savedCalls = 0
        val creation = EditorCreation(
            scope = modelScope,
            restoredId = 42L,
            create = { error("The ready editor must not insert another record") },
            discard = { error("A saved editor must not discard its record") },
        )
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalNavigationEntryActive provides active.value) {
                    NewEditorDestination(
                        requestId = "new-table",
                        title = R.string.edit_table_title,
                        creationFor = { creation },
                        onDiscard = { error("This test does not navigate back") },
                        onSaved = { savedCalls++ },
                    ) { id -> Text("Editor $id", Modifier.testTag("editor")) }
                }
            }
        }
        compose.onNodeWithTag("editor").assertTextEquals("Editor 42")
        compose.runOnIdle {
            assertTrue(creation.tryBeginSave())
            creation.trackSave(write)
            active.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle { write.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithTag("editor").assertTextEquals("Editor 42")
        compose.runOnIdle {
            assertEquals(EditorCreationState.Saved(42L), creation.state.value)
            assertEquals(0, savedCalls)
            active.value = true
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, savedCalls); active.value = false }
        compose.waitForIdle()
        compose.runOnIdle { active.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, savedCalls) }
    }

    @Test
    fun recreatedEntryReusesItsRecordAndConsumesRetainedSaveCompletionOnce() {
        val visible = mutableStateOf(true)
        val write = CompletableDeferred<Unit>()
        val restoredIds = mutableListOf<Long?>()
        var insertCalls = 0
        var savedCalls = 0
        val creation = EditorCreation(
            scope = modelScope,
            create = { insertCalls++; 73L },
            discard = { error("A saved editor must not discard its record") },
        )
        compose.setContent {
            MaterialTheme {
                val holder = rememberSaveableStateHolder()
                if (visible.value) holder.SaveableStateProvider("new-table-entry") {
                    NewEditorDestination(
                        requestId = "new-table",
                        title = R.string.edit_table_title,
                        creationFor = { restoredId ->
                            restoredIds += restoredId
                            creation
                        },
                        onDiscard = { error("Recomposition must not discard the record") },
                        onSaved = { savedCalls++ },
                    ) { id -> Text("Editor $id", Modifier.testTag("editor")) }
                }
            }
        }
        compose.onNodeWithTag("editor").assertTextEquals("Editor 73")
        compose.runOnIdle {
            assertEquals(1, insertCalls)
            assertTrue(creation.tryBeginSave())
            creation.trackSave(write)
            visible.value = false
        }
        compose.onNodeWithTag("editor").assertDoesNotExist()
        compose.runOnIdle { write.complete(Unit) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(EditorCreationState.Saved(73L), creation.state.value)
            assertEquals(0, savedCalls)
            visible.value = true
        }
        compose.onNodeWithTag("editor").assertTextEquals("Editor 73")
        compose.runOnIdle {
            assertEquals(1, savedCalls)
            assertEquals(1, insertCalls)
            assertEquals(listOf(null, 73L), restoredIds)
            visible.value = false
        }
        compose.onNodeWithTag("editor").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("editor").assertTextEquals("Editor 73")
        compose.runOnIdle {
            assertEquals(1, savedCalls)
            assertEquals(1, insertCalls)
            assertEquals(listOf(null, 73L, 73L), restoredIds)
        }
    }
}
