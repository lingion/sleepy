package com.lingion.sleepy.ui.screen.imports

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.data.jw.JwProtocol
import com.lingion.sleepy.data.jw.JwSchoolInfo
import com.lingion.sleepy.data.repository.ImportDraftRepository
import com.lingion.sleepy.data.repository.ScheduleRepository
import com.lingion.sleepy.data.undo.UndoManager
import com.lingion.sleepy.ui.theme.UiStyle
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.widget.notification.ReminderRescheduler
import java.lang.reflect.Field
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Exercise the real school picker, login screen and Activity back dispatcher. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h800dp-port-xhdpi", application = Application::class)
@OptIn(ExperimentalTestApi::class)
class JwImportBackNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var context: Application
    private lateinit var database: AppDatabase
    private lateinit var previousUiStyle: UiStyle
    private var activity: ActivityScenario<JwImportActivity>? = null
    private val replacedFields = mutableListOf<RestoredField>()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        previousUiStyle = AppPrefs.getUiStyle(context)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = ScheduleRepository(database)
        val drafts = ImportDraftRepository(database)

        // Match the existing real-Activity fixture without starting unrelated app workers.
        val app = SleepyApp()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
            .invoke(app, context)
        replaceField(SleepyApp::class.java, app, "database\$delegate", lazyOf(database))
        replaceField(SleepyApp::class.java, app, "repository\$delegate", lazyOf(repository))
        replaceField(SleepyApp::class.java, app, "importDraftRepository\$delegate", lazyOf(drafts))
        replaceField(AppDatabase::class.java, null, "instance", database)
        replaceField(SleepyApp::class.java, null, "instance", app)
        ReminderRescheduler.resetForTest()
        UndoManager.clear()
    }

    @After fun cleanup() {
        try {
            activity?.close()
        } finally {
            try {
                if (::context.isInitialized && ::previousUiStyle.isInitialized) {
                    AppPrefs.setUiStyle(context, previousUiStyle)
                }
                ReminderRescheduler.resetForTest()
                shadowOf(Looper.getMainLooper()).idle()
                if (::database.isInitialized) database.close()
            } finally {
                replacedFields.asReversed().forEach { it.field.set(it.owner, it.originalValue) }
                replacedFields.clear()
                UndoManager.clear()
            }
        }
    }

    @Test fun materialToolbarBackReturnsToTheFilteredSchoolList() {
        verifyReturnToSchoolList(UiStyle.Classic, systemBack = false)
    }

    @Test fun glassToolbarBackReturnsToTheFilteredSchoolList() {
        verifyReturnToSchoolList(UiStyle.LiquidGlass, systemBack = false)
    }

    @Test fun materialSystemBackWithoutWebHistoryReturnsToTheFilteredSchoolList() {
        verifyReturnToSchoolList(UiStyle.Classic, systemBack = true)
    }

    @Test fun glassSystemBackWithoutWebHistoryReturnsToTheFilteredSchoolList() {
        verifyReturnToSchoolList(UiStyle.LiquidGlass, systemBack = true)
    }

    private fun verifyReturnToSchoolList(style: UiStyle, systemBack: Boolean) {
        AppPrefs.setUiStyle(context, style)
        val school = supportedSchool()
        val scenario = ActivityScenario.launch<JwImportActivity>(Intent(context, JwImportActivity::class.java))
        activity = scenario
        awaitState { compose.onAllNodesWithTag("school_search").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("school_search").performTextInput(school.name)
        compose.onNode(hasTestTag("school_item") and hasText(school.name)).performScrollTo().performClick()
        awaitState { compose.onAllNodesWithTag("jw_back").fetchSemanticsNodes().isNotEmpty() }

        // Robolectric's WebView shadow records loadUrl without fetching the school's network.
        // Assert the actual live View has no history, rather than forcing a production back hook.
        scenario.onActivity {
            val webView = findWebView(it.window.decorView)
            assertNotNull("The actual school login WebView must be mounted", webView)
            assertFalse("The newly opened school page must have no backward history", webView!!.canGoBack())
        }
        if (systemBack) {
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        } else {
            compose.onNodeWithTag("jw_back").performClick()
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Leaving the login page must keep the import Activity resumed", Lifecycle.State.RESUMED, scenario.state)
        scenario.onActivity { assertFalse("Login back must not finish the import Activity", it.isFinishing) }
        awaitState { compose.onAllNodesWithTag("school_search").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("school_search").assertTextContains(school.name)
        compose.onNode(hasTestTag("school_item") and hasText(school.name)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("jw_back").assertDoesNotExist()

        // Only the second back, from the school list itself, exits to the caller.
        compose.onNodeWithTag("school_back").performClick()
        scenario.onActivity {
            assertTrue("School-list back must finish the import Activity", it.isFinishing)
        }
    }

    private fun supportedSchool(): JwSchoolInfo {
        val schools = context.assets.open("schools.json").bufferedReader().use {
            JwImportViewModel.parseSchoolsJson(it.readText())
        }
        val nameCounts = schools.groupingBy { it.name }.eachCount()
        return schools.first {
            it.canImport && it.isSupported && !it.isGrad &&
                it.type == JwProtocol.TYPE_ZF_NEW && nameCounts[it.name] == 1
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun awaitState(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
        compose.waitForIdle()
    }

    private fun replaceField(type: Class<*>, owner: Any?, name: String, value: Any?) {
        val field = type.getDeclaredField(name).apply { isAccessible = true }
        replacedFields += RestoredField(field, owner, field.get(owner))
        field.set(owner, value)
    }

    private data class RestoredField(val field: Field, val owner: Any?, val originalValue: Any?)
}
