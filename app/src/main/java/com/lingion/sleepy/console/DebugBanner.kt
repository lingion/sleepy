package com.lingion.sleepy.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.console.ui.dbgTag

/**
 * release 解锁态顶栏红条（Task 7, SPEC §5.3 降级可见性）：
 * isReleaseUnlocked=true 时常驻显示 "DEBUG UNLOCKED" + 一键失活按钮。
 *
 * 接入：根 setContent 的 MaterialTheme 内容顶层已插一行(见 MainActivity/AppRoot);
 * debug 构建不显示（debug 全开是常态, 红条只服务 release 的异常解锁态）。
 * 失活链: DebugGate.invalidate + TokenStore.revoke + ConsoleBootstrap.stop
 * （stop 内部已带前两者, 单入口调用）。
 */
@Composable
fun DebugModeBanner() {
    if (!DebugGate.isReleaseUnlocked || DebugGate.debugBuild) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "DEBUG UNLOCKED",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .weight(1f)
                .dbgTag("debug_banner", role = "text")
        )
        TextButton(
            onClick = { ConsoleBootstrap.stop() },
            modifier = Modifier.dbgTag("debug_banner_deactivate", text = "DEACTIVATE", role = "button")
        ) {
            Text(
                text = "DEACTIVATE",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
