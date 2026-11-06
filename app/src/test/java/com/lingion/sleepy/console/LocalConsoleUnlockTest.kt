package com.lingion.sleepy.console

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lingion.sleepy.console.handlers.EnableFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalConsoleUnlockTest {
    @After fun cleanUp() { ConsoleBootstrap.stop(); DebugGate.reset(); TokenStore.reset() }

    @Test fun localReleaseUnlockRegistersCapabilitiesAndStopRevokesSession() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(ConsoleBootstrap.armForUnlock(app))
        // 同一个 debug 测试产物内模拟 release 门位，使用测试密钥。
        DebugGate.reset()
        TokenStore.reset(passphrase = "test-only-key")
        CommandRouter.reset()
        DebugGate.armUnlock()
        assertEquals(EnableFlow.Result.WRONG_PASSPHRASE, ConsoleBootstrap.unlock("wrong"))
        assertFalse(DebugGate.status.value.active)
        assertEquals(EnableFlow.Result.OK, ConsoleBootstrap.unlock("test-only-key"))
        assertTrue(DebugGate.status.value.releaseUnlocked)
        assertTrue(CommandRouter.registeredPaths().contains("/v1/data/prefs"))
        val token = TokenStore.issue()
        assertTrue(TokenStore.verify(token))
        ConsoleBootstrap.stop()
        assertFalse(DebugGate.status.value.active)
        assertFalse(TokenStore.verify(token))
    }
}
