//go:build windows

// console_windows.go: Windows 控制台中文兼容。
// 旧版 cmd.exe 默认代码页是 GBK(cp936),程序输出的 UTF-8 中文会变成乱码。
// 启动时把控制台输入/输出代码页切成 UTF-8(65001);Win10 及以上渲染正常,
// 更老的系统上 API 调用静默失败,不影响其余功能。

package main

import "syscall"

const consoleCPUTF8 = 65001

func init() {
	k32 := syscall.NewLazyDLL("kernel32.dll")
	_, _, _ = k32.NewProc("SetConsoleOutputCP").Call(consoleCPUTF8)
	_, _, _ = k32.NewProc("SetConsoleCP").Call(consoleCPUTF8)
}
