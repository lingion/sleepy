package com.lingion.sleepy.debug

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.lingion.sleepy.console.ConsoleBootstrap
import com.lingion.sleepy.debug.console.TriggerHandler

/**
 * debug 源集的 console 自动装配入口 — release APK 物理不含本类（SPEC §5.1）。
 *
 * Android 在 Application.onCreate 之前创建 provider，因此 console 在首帧前就绪，
 * adb forward 之后 curl /v1/hello 即可探活。onCreate 返回 true 后
 * ConsoleBootstrap.onCreate 依 BuildConfig.DEBUG 无条件 start。
 *
 * debug-only 端点（后续任务的 trigger/mock 组件）在 start 之前经
 * ConsoleBootstrap.addHandler 注册 — 这里是那个通道的既定时序位。
 */
class DebugConsoleProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.let { app ->
            // debug-only 端点必须在服务器 start 之前注册（既定扩展时序位, Task 5）
            TriggerHandler.registerAll(app)
            ConsoleBootstrap.onCreate(app)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
