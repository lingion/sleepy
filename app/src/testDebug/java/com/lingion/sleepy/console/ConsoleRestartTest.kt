package com.lingion.sleepy.console

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.debug.DebugConsoleProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ConsoleRestartTest {
    @After fun cleanUp() { ConsoleBootstrap.stop(); DebugGate.reset(); TokenStore.reset() }

    @Test fun providerCapabilitiesSurviveClosingAndReopeningConsole() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Robolectric.buildContentProvider(DebugConsoleProvider::class.java).create()
        val originalPaths = CommandRouter.registeredPaths()
        assertTrue(originalPaths.contains("/v1/trigger"))
        val oldToken = TokenStore.issue()
        assertNotNull(oldToken)
        ConsoleBootstrap.stop()
        assertFalse(TokenStore.verify(oldToken))
        assertTrue(CommandRouter.registeredPaths().isEmpty())
        assertTrue(ConsoleBootstrap.armForUnlock(app))
        assertTrue(DebugGate.isActive)
        assertEquals(originalPaths, CommandRouter.registeredPaths())
        assertFalse(TokenStore.verify(oldToken))
        assertTrue(TokenStore.verify(TokenStore.issue()))
        // 错误请求应由真实 trigger handler 拒绝，而不是路由不存在。
        val response = CommandRouter.dispatch("/v1/trigger", "POST", ConsoleRequest("{}"))
        assertTrue(response.contains("type must be one of"))
    }
}
