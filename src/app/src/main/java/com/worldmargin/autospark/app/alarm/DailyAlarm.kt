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
 *                   → root + monkey 拉起抖音（不亮屏）
 *                   → 抖音进程启动 → Application.attach 被 hook
 *                   → AutoSparkBridge.scheduleOnAppStart() → 扫描 + 发送
 *                   → AlarmReceiver 检测到已发 → am force-stop 关掉
 * ```
 *
 * 发送本身不需要屏幕亮着、也不需要解锁 —— 它只走 IM SDK 的发送链路。
 *
 * ## 为什么用 monkey 而不是 startActivity
 *
 * `startActivity` 在后台会被 `backgroundActivityAllowed=false` 拦（实测
 * `dumpsys alarm` 的 idle-options 里是这个值）。`monkey -p <pkg> -c LAUNCHER 1`
 * 走 AMS 的注入通道，实测能在**锁屏 + Doze** 状态下拉起抖音且**不点亮屏幕**。
 *
 * 代价：monkey 只接受 root 或 shell 身份，普通 App 身份执行会被 `Killed`。
 * **所以这条路依赖 root。没 root 时直接失败，不降级到 startActivity。**
 *
 * ## 权限
 *
 * - Android 12+ 精确闹钟需要 `SCHEDULE_EXACT_ALARM`。没拿到时退化成
 *   [AlarmManager.setAndAllowWhileIdle]（不精确，doze 下最多晚十几分钟，够用）。
 */
object DailyAlarm {
    private const val REQUEST_CODE = 0x5A17

    const val ACTION_ALARM = "com.worldmargin.autospark.action.DAILY_ALARM"

    /** 抖音包名。monkey 用它找入口，硬编码比动态查询可靠。 */
    private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"

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

    /** 精确闹钟是否可用（Android 12 以下恒为 true）。 */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return manager.canScheduleExactAlarms()
    }

    /**
     * 拉起抖音，**只走 root + monkey**。
     *
     * 为什么不用 startActivity：
     * - Android 10+ 的 `backgroundActivityAllowed=false` 会让后台 startActivity 被拦；
     * - `monkey -p <pkg> -c LAUNCHER 1` 是系统 shell 工具，走的是 AMS 的注入通道，
     *   实测**不点亮屏幕**（`mWakefulness` 保持 Asleep）且能清掉 `stopped=true`；
     * - 但它要求调用方是 root 或 shell，普通 App 身份跑会被 `Killed`。
     *
     * 没有 root 就直接失败 —— 不降级到 startActivity。原因：startActivity 会亮屏、
     * 打断用户正在用的应用，且大概率被 BAL 拦掉，属于「看着像在工作其实不可靠」。
     * 与其静默降级，不如让设置页明确显示「需要 root」。
     *
     * @return 用了哪条路，用于日志对账。
     */
    fun launchDouyin(): LaunchMethod =
        RootShell.monkeyLaunch(DOUYIN_PACKAGE) ?: LaunchMethod.FAILED

    /** 把抖音关掉。 */
    fun stopDouyin(): Boolean = RootShell.amKill(DOUYIN_PACKAGE)

    /** 拉起方式，用于日志与界面提示。 */
    enum class LaunchMethod {
        /** root + monkey，不亮屏 */
        MONKEY,

        /** 没成功（没 root / monkey 被拒 / 抖音没装） */
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
}
