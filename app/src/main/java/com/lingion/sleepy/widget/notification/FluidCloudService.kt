package com.lingion.sleepy.widget.notification

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.R
import com.lingion.sleepy.util.AppClock

/**
 * 进程内投放状态真源: 服务存活 = 投放中。
 * 不用通知可见性当信号 — ColorOS 16 等系统在应用回前台时自动收起 promoted
 * 实时通知 (activeNotifications 查不到), 但服务仍在投 (2026-10-10 用户实测)。
 * UI 据此对账; 服务启动路径置位, 停止路径/onDestroy 清零。进程死则归零。
 */
object FluidCastState {
    @Volatile
    var casting: Boolean = false
        private set
    @Volatile
    var testCasting: Boolean = false
        private set

    fun markStarted(test: Boolean = false) { casting = true; testCasting = test }
    fun markStopped() { casting = false; testCasting = false }
}

/**
 * Keeps the promoted course notification's progress synchronized with the
 * user's before-class reminder window. The capsule text remains static.
 */
class FluidCloudService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var courseName = ""
    private var room = ""
    private var teacher = ""
    private var startTime = ""
    private var endTime = ""
    private var startNode = 0
    private var notifyEpoch = 0L
    private var classEpoch = 0L
    private var activeIdentity: FluidNotificationIdentity? = null
    private val vendor by lazy { detectLiveCardVendor() }
    private val session = FluidNotificationSession(
        now = System::currentTimeMillis,
        schedule = { callback, delay -> handler.postDelayed(callback, delay) },
        cancel = { callback -> handler.removeCallbacks(callback) },
        publish = ::postProgressNotification,
        finished = ::stopCloudNotification,
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CourseNotificationScheduler.ensureNotificationChannels(this)
        val preserveTest = intent?.getBooleanExtra(EXTRA_RECONCILE, false) == true &&
            FluidCastState.testCasting && classEpoch > AppClock.millis()
        if (intent?.action == ACTION_STOP) {
            if (!preserveTest) stopCloudNotification()
            return START_NOT_STICKY
        }
        // Recheck on the service thread: the database lookup may predate a new test.
        if (preserveTest) return START_NOT_STICKY
        val nextIdentity = FluidNotificationIdentity(
            intent?.getLongExtra("notifyEpoch", 0L) ?: 0L,
            intent?.getLongExtra("classEpoch", 0L) ?: 0L,
            intent?.getIntExtra("startNode", 0) ?: 0,
            intent?.getStringExtra("courseName").orEmpty(),
        )
        val reuseSession = canReuseFluidSession(
            activeIdentity, nextIdentity, FluidCastState.casting,
            intent?.action == ACTION_TEST, AppClock.millis(),
        )
        if (!reuseSession) {
            session.stop()
            cancelVivoCard(this)
        }
        if (intent?.action == ACTION_TEST) {
            // 流体云测试入口: 用示例课程强制唤起一次, 窗口 2 分钟(进度条真实推进)。
            val now = AppClock.millis()
            courseName = getString(R.string.reminder_fluid_test_course)
            room = getString(R.string.reminder_fluid_test_room)
            teacher = ""
            startTime = android.text.format.DateFormat.format("HH:mm", now + TEST_WINDOW_MS).toString()
            endTime = android.text.format.DateFormat.format("HH:mm", now + TEST_WINDOW_MS + 45 * 60_000L).toString()
            startNode = 1
            notifyEpoch = now
            classEpoch = now + TEST_WINDOW_MS
        } else {
            courseName = intent?.getStringExtra("courseName") ?: getString(R.string.default_course_name)
            room = intent?.getStringExtra("room").orEmpty().ifBlank { getString(R.string.default_room) }
            teacher = intent?.getStringExtra("teacher").orEmpty()
            startTime = intent?.getStringExtra("startTime").orEmpty()
            endTime = intent?.getStringExtra("endTime").orEmpty()
            startNode = intent?.getIntExtra("startNode", 0) ?: 0
            notifyEpoch = intent?.getLongExtra("notifyEpoch", 0L) ?: 0L
            classEpoch = intent?.getLongExtra("classEpoch", 0L) ?: 0L
        }

        if (notifyEpoch <= 0L || classEpoch <= notifyEpoch) {
            val now = AppClock.millis()
            notifyEpoch = now
            classEpoch = now + 1L
        }

        if (classEpoch <= AppClock.millis()) {
            stopCloudNotification()
            return START_NOT_STICKY
        }

        // A repeated scheduler start still refreshes display fields above. Publish them
        // on the existing tick to preserve vivo's rate limit and expanded card state.
        if (reuseSession) return START_NOT_STICKY
        activeIdentity = nextIdentity
        FluidCastState.markStarted(intent?.action == ACTION_TEST)
        session.start(
            classEpoch,
            intent?.action == ACTION_TEST && needsFluidFirstPostReinforcement(vendor),
        )
        return START_NOT_STICKY
    }

    private fun postProgressNotification(updateSequence: Int) {
        val now = AppClock.millis()
        val state = CourseLiveCardState(
            courseName = courseName,
            room = room,
            teacher = teacher,
            startTime = startTime,
            notifyEpoch = notifyEpoch,
            classEpoch = classEpoch,
            nowEpoch = now,
            updateSequence = updateSequence,
            endTime = endTime,
            startNode = startNode
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = VendorLiveCardRenderer.build(
            context = this,
            state = state,
            contentIntent = contentIntent,
            channelId = CourseNotificationScheduler.CHANNEL_FLUID,
            vendor = vendor,
        )

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            // 前台服务路径: startForeground 本身不需要 POST_NOTIFICATIONS 运行时权限
            startForeground(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, notification)
        } else {
            // Lint MissingPermission: 前台服务由 startForegroundService 启动链路触发,
            //   但 API<26 notify 分支仍需权限校验兜底(权限被拒时静默跳过, 不抛 SecurityException)
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.NotificationManagerCompat.from(this)
                    .notify(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, notification)
            }
        }
        android.util.Log.d(
            "FluidCloudService",
            "updated course progress=${state.progress} notify=$notifyEpoch class=$classEpoch"
        )
    }

    private fun stopCloudNotification() {
        activeIdentity = null
        session.stop()
        FluidCastState.markStopped()
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            try {
                val placeholder = NotificationCompat.Builder(this, CourseNotificationScheduler.CHANNEL_FLUID)
                    .setSmallIcon(R.drawable.ic_notification_time)
                    .setContentTitle(getString(R.string.reminder_fluid_title))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                startForeground(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, placeholder)
            } catch (_: Throwable) {}
        }
        androidx.core.app.NotificationManagerCompat.from(this)
            .cancel(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE)
        cancelVivoCard(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        session.stop()
        cancelVivoCard(this)
        FluidCastState.markStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TEST_WINDOW_MS = 2 * 60_000L
        const val ACTION_TEST = "com.lingion.sleepy.action.FLUID_TEST"
        const val ACTION_STOP = "com.lingion.sleepy.action.FLUID_STOP"
        internal const val EXTRA_RECONCILE = "fluidReconcile"

        // Official vivo doc 896: direct cancellation uses the system-created tag.
        // Keep normal cancellation as well: the foreground notification is untagged.
        private fun cancelVivoCard(context: android.content.Context) {
            val vendor = detectLiveCardVendor()
            if (vendor == LiveCardVendor.VIVO || vendor == LiveCardVendor.IQOO) {
                androidx.core.app.NotificationManagerCompat.from(context)
                    .cancel("VIVO_SUPERX_TAG", CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE)
            }
        }

        fun requestStop(context: android.content.Context, reconciliation: Boolean = false) {
            if (reconciliation && FluidCastState.testCasting) return
            val intent = Intent(context, FluidCloudService::class.java).setAction(ACTION_STOP)
                .putExtra(EXTRA_RECONCILE, reconciliation)
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (_: Throwable) {
                if (reconciliation && FluidCastState.testCasting) return
                cancelVivoCard(context)
                androidx.core.app.NotificationManagerCompat.from(context)
                    .cancel(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE)
            }
        }
    }
}
