package com.lingion.sleepy.console

/**
 * 进程级审计环 — 唯一写入方 DebugConsoleServer.serve()，唯一读取方 LogHandler。
 *
 * 挂进程不挂服务器实例：stop()/重绑不丢审计证据（测试与解锁重装配后
 * /v1/log 仍可回看历史请求 — 实例级环曾令重绑后审计恒空）。
 */
object ConsoleAudit {
    val log = AuditLog()
}
