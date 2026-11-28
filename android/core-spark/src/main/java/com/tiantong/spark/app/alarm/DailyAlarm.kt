package com.tiantong.spark.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.util.Calendar

/**
 * 定时唤醒：用 AlarmManager 在每天固定时刻把抖音拉起来。
 *
 * - [schedule] 主入口：排下一次闹钟 + 登记/取消 15 分钟兜底 Job（App 启动、改设置、闹钟响后用）。
 * - [arm] 只排「下一次闹钟」，**不碰**兜底 Job。兜底 Job 自己重排时必须用这个，否则会自触发死循环。
 * - [scheduleRetryAt] 安排一次「补发」闹钟（一次性）。
 * - [launchDouyin] 优先免 root（preload provider），失败回退 root + monkey。
 */
object DailyAlarm {
    private const val REQUEST_CODE = 0x5A17
    private const val RETRY_REQUEST_CODE = 0x5A19

    const val ACTION_ALARM = "com.tiantong.spark.action.DAILY_ALARM"
    const val ACTION_RETRY = "com.tiantong.spark.action.RETRY_ALARM"

    private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"

    /** 今天 hour:minute 是否已经过了；过了就取明天。返回毫秒时间戳。 */
    fun nextTriggerAt(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    /** 只排「下一次闹钟」，不碰兜底 Job（兜底 Job 内部重排专用，避免自触发）。 */
    fun arm(context: Context, hour: Int, minute: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context) ?: return
        runCatching { manager.cancel(pending) }
        if (hour !in 0..23) return
        val at = nextTriggerAt(hour, minute)
        if (at <= 0L) return
        setExact(manager, at, pending)
    }

    /** 主入口：排闹钟 + 登记/取消兜底 Job。 */
    fun schedule(context: Context, hour: Int, minute: Int) {
        if (hour !in 0..23 || nextTriggerAt(hour, minute) <= 0L) {
            cancel(context)
            ScheduleGuardService.cancel(context)
            return
        }
        arm(context, hour, minute)
        ScheduleGuardService.schedule(context)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context) ?: return
        runCatching { manager.cancel(pending) }
    }

    /** 安排一次「补发」闹钟（一次性）：到点只拉起抖音，由抖音进程跑补发轮。 */
    fun scheduleRetryAt(context: Context, at: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = retryPendingIntent(context) ?: return
        runCatching { manager.cancel(pending) }
        if (at <= System.currentTimeMillis()) return
        setExact(manager, at, pending)
    }

    fun cancelRetry(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = retryPendingIntent(context) ?: return
        runCatching { manager.cancel(pending) }
    }

    private fun setExact(manager: AlarmManager, at: Long, pending: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        runCatching {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }.onFailure {
            // 没有精确闹钟权限时退化为非精确，别让功能整体失效
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    /**
     * 拉起抖音：先试免 root 的 `depths.pre.load` 内容提供者（不弹窗、不需要 root），
     * 失败再回退 root + monkey。
     */
    fun launchDouyin(context: Context): LaunchMethod {
        if (tryPreload(context)) return LaunchMethod.PRELOAD
        return RootShell.monkeyLaunch(DOUYIN_PACKAGE) ?: LaunchMethod.FAILED
    }

    private fun tryPreload(context: Context): Boolean =
        runCatching {
            val uri = Uri.parse("content://$DOUYIN_PACKAGE.depths.pre.load")
            val client = context.contentResolver.acquireUnstableContentProviderClient(uri)
            if (client != null) {
                runCatching { client.close() }
                true
            } else {
                false
            }
        }.getOrDefault(false)

    fun stopDouyin(): Boolean = RootShell.amKill(DOUYIN_PACKAGE)

    enum class LaunchMethod {
        PRELOAD,
        MONKEY,
        FAILED,
    }

    private fun pendingIntent(context: Context): PendingIntent? =
        runCatching {
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, AlarmReceiver::class.java).setAction(ACTION_ALARM),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }.getOrNull()

    private fun retryPendingIntent(context: Context): PendingIntent? =
        runCatching {
            PendingIntent.getBroadcast(
                context,
                RETRY_REQUEST_CODE,
                Intent(context, AlarmReceiver::class.java).setAction(ACTION_RETRY),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }.getOrNull()
}
