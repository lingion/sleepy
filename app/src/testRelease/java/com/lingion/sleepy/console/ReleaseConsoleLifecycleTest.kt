package com.lingion.sleepy.console

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.BuildConfig
import com.lingion.sleepy.console.handlers.EnableFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReleaseConsoleLifecycleTest {
    @After fun cleanUp() { ConsoleBootstrap.stop(); DebugGate.reset(); TokenStore.reset() }

    @Test fun releaseReopensWithFreshTokenAndRecoversFromPortConflict() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertFalse(BuildConfig.DEBUG)
        ConsoleBootstrap.onCreate(app)
        assertFalse(DebugGate.isActive)
        // 未解锁时不监听；占用实际端口验证绑定失败及重试。
        ServerSocket(DebugConsoleServer.PORT, 1, InetAddress.getByName("127.0.0.1")).use {
            DebugGate.armUnlock()
            assertFalse(ConsoleBootstrap.armForUnlock(app))
            assertFalse(DebugGate.isActive)
            assertNull(TokenStore.issue())
        }
        assertTrue(ConsoleBootstrap.armForUnlock(app))
        assertEquals(EnableFlow.Result.OK, ConsoleBootstrap.unlock(BuildConfig.CONSOLE_ENABLE_PASSPHRASE))
        val paths = CommandRouter.registeredPaths()
        assertTrue(paths.contains("/v1/data/prefs"))
        assertFalse(paths.contains("/v1/trigger"))
        val oldToken = TokenStore.issue()
        assertTrue(TokenStore.verify(oldToken))
        ConsoleBootstrap.stop()
        assertFalse(DebugGate.isActive)
        assertFalse(TokenStore.verify(oldToken))
        DebugGate.armUnlock()
        assertTrue(ConsoleBootstrap.armForUnlock(app))
        assertEquals(EnableFlow.Result.OK, ConsoleBootstrap.unlock(BuildConfig.CONSOLE_ENABLE_PASSPHRASE))
        assertEquals(paths, CommandRouter.registeredPaths())
        assertFalse(TokenStore.verify(oldToken))
        assertTrue(TokenStore.verify(TokenStore.issue()))
    }
}
