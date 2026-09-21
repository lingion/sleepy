package com.lingion.sleepy.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import com.lingion.sleepy.data.entity.CourseEntity

/**
 * issue#45: 官方 androidx.navigation3 的极薄封装。
 *
 * 决策:栈本体直接用官方 [NavBackStack](rememberNavBackStack 的产物) —
 * 进程死亡/配置变更的持久化由官方 NavBackStackSerializer(反射式 NavKeySerializer)
 * 承担,SleepyRoute 全系 @Serializable,不需要自写 Saver。仿 KernelSU ui/navigation3/Navigator
 * 把所有「栈意图」封成命名方法 — 不对外暴露栈的内部形态,只暴露 push/pop/popToMain/openXxx。
 *
 * [editingCourse] 是会话态:基线 §1.3 明确不纳入任何保存作用域, 旋转/进程恢复安全丢弃。
 * 纯 mutableStateOf,不 rememberSaveable — 与栈的序列化持久化刻意隔离。
 */
class SleepyNavigator(
    val backStack: NavBackStack<NavKey>,
    val session: NavSession,
) {
    fun push(route: SleepyRoute) {
        if (backStack.lastOrNull() == route) return
        backStack.add(route)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun popToMain() {
        while (backStack.size > 1 && backStack.last() != SleepyRoute.Main) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun openAddCourse(courseId: Long = NavSession.NO_ID, editing: Boolean = false) =
        push(SleepyRoute.AddCourse(courseId, editing))
    fun openAllTables() = push(SleepyRoute.AllTables)
    fun openAppearance() = push(SleepyRoute.Appearance)
    fun openGeneral() = push(SleepyRoute.General)
    fun openHoliday() = push(SleepyRoute.Holiday)
    fun openExport() = push(SleepyRoute.Export)
    fun openReminder() = push(SleepyRoute.Reminder)
    fun openAbout() = push(SleepyRoute.About)
    fun openLicense() = push(SleepyRoute.License)
    fun openWidgetManagement() = push(SleepyRoute.WidgetManagement)
    fun openPeriodTables() = push(SleepyRoute.PeriodTables)
    fun openWidgetEdit(widgetId: Int) = push(SleepyRoute.WidgetEdit(widgetId))
    fun openPeriodEdit(periodId: Long, isNew: Boolean = false) =
        push(SleepyRoute.PeriodEdit(periodId, isNew))
    fun openEditTable(
        tableId: Long = NavSession.NO_ID,
        pendingNew: Long = NavSession.NO_ID,
        prevDefault: Long = NavSession.NO_ID,
    ) = push(SleepyRoute.EditTable(tableId, pendingNew, prevDefault))
    fun createPeriodTableAndEdit(newId: Long) = openPeriodEdit(newId, isNew = true)
}

/**
 * 把 SleepyNavigator 注入 AppRoot。栈持久化交给官方 rememberNavBackStack(Android 反射
 * 序列化器, sealed @Serializable 路由免注册);session 是会话态(不持久化)。
 */
@Composable
fun rememberSleepyNavigator(initial: SleepyRoute = SleepyRoute.Main): SleepyNavigator {
    val session = remember { NavSession() }
    val backStack = rememberNavBackStack(initial)
    return remember(backStack, session) { SleepyNavigator(backStack, session) }
}

class NavSession {
    var editingCourse: CourseEntity? by mutableStateOf(null)
        private set
    fun beginEditCourse(course: CourseEntity) { editingCourse = course }
    fun clearEditCourse() { editingCourse = null }
    companion object { const val NO_ID = SleepyRoute.NO_ID }
}
