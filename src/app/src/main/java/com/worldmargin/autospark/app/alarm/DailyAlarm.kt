package com.worldmargin.autospark.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 每天定时把抖音拉起来，让模块有机会跑一轮自动续火花。
 *
 * ## 为什么需要它
 *
 * 模块的代码跑在**抖音进程里**（LSPosed 注入）。抖音没启动 = 没有任何代码在跑，
 * 所以「锁屏状态下也能自动发」只能靠外部把抖音启动起来：
 *
 * ```
 * AlarmManager 到点 → AlarmReceiver（本模块进程）
 *                   → startActivity(抖音 SplashActivity)
 *                   → 抖音进程启动 → Application.attach 被 hook
 *                   → AutoSparkBridge.scheduleOnAppStart() → 扫描 + 发送
 * ```
 *
 * 发送本身不需要屏幕亮着、也不需要解锁 —— 它只走 IM SDK 的发送链路。
 *
 * ## 权限
 *
 * - Android 10+ 禁止后台启动 Activity。本模块申请 `SYSTEM_ALERT_WINDOW`（悬浮窗），
 *   拿到它之后系统把本应用当作「可见应用」，后台 startActivity 才放行。
 * - Android 12+ 精确闹钟需要 `SCHEDULE_EXACT_ALARM`。没拿到时退化成
 *   [AlarmManager.setAndAllowWhileIdle]（不精确，doze 下最多晚十几分钟，够用）。
 */
object DailyAlarm {
    private const val REQUEST_CODE = 0x5A17

    const val ACTION_ALARM = "com.worldmargin.autospark.action.DAILY_ALARM"

    /** 抖音的桌面入口。硬编码比动态查询可靠（抖音改 Activity 名时这里跟着改）。 */
    private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
    private const val DOUYIN_SPLASH = "com.ss.android.ugc.aweme.splash.SplashActivity"

    /** 下一次触发的时间戳；[hour] 为 -1 时返回 0（未启用）。 */
    fun nextTriggerAt(
        hour: Int,
        minute: Int,
        now: Long = System.currentTimeMillis(),
    ): Long {
        if (hour !in 0..23) return 0L
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        // 今天这个点已经过了（或正好是现在）→ 排到明天
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    /**
     * 按配置重排闹钟。**每次 App 启动 / 改设置 / 闹钟响过之后都要调一次**，
     * 因为精确闹钟是一次性的，响完要自己排下一天。
     */
    fun schedule(
        context: Context,
        hour: Int,
        minute: Int,
    ) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val pending = pendingIntent(context)
        if (manager == null || pending == null) return
        cancel(context)
        if (hour !in 0..23) return
        val at = nextTriggerAt(hour, minute)
        if (at <= 0L) return

        // 先试精确闹钟；没权限就退到「doze 也能响但不精确」
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        runCatching {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }.onFailure {
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context) ?: return
        runCatching { manager.cancel(pending) }
    }

    /** 本应用是否已被允许后台弹出界面（`SYSTEM_ALERT_WINDOW`）。 */
    fun canStartFromBackground(context: Context): Boolean =
        android.provider.Settings.canDrawOverlays(context)

    /** 精确闹钟是否可用（Android 12 以下恒为 true）。 */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return manager.canScheduleExactAlarms()
    }

    /** 拉起抖音。失败时返回 false（没装 / 被系统拦）。 */
    fun launchDouyin(context: Context): Boolean {
        val intent =
            Intent(Intent.ACTION_MAIN).apply {
                setClassName(DOUYIN_PACKAGE, DOUYIN_SPLASH)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            }
        val ok = runCatching { context.startActivity(intent) }.isSuccess
        if (ok) return true
        // SplashActivity 名字变了（抖音升级）时退回桌面入口
        val fallback =
            context.packageManager.getLaunchIntentForPackage(DOUYIN_PACKAGE)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        return fallback != null && runCatching { context.startActivity(fallback) }.isSuccess
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
}
