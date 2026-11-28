package com.tiantong.spark.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
 *                   → 优先免 root：抖音 preload provider 拉起主进程
 *                     （不可达则回退 root + monkey，不亮屏）
 *                   → 抖音进程启动 → Application.attach 被 hook
 *                   → scheduleOnAppStart() → 扫描 + 发送
 *                   → AlarmReceiver 检测到已发 → am force-stop 关掉
 * ```
 *
 * 发送本身不需要屏幕亮着、也不需要解锁 —— 它只走 IM SDK 的发送链路。
 *
 * ## 为什么优先免 root、再回退 monkey
 *
 * 免 root 走 `content://<抖音包>.depths.pre.load` 的 provider client，取 client 会顺带
 * 把抖音主进程拉起来，不需要 su；provider 不可达时再回退 root + `monkey`
 * （`monkey -p <pkg> -c LAUNCHER 1` 走 AMS 注入通道，锁屏 + Doze 下能拉起且不亮屏，
 * 但只接受 root / shell 身份，普通 App 执行会被 `Killed`）。
 *
 * 两条路都不用 startActivity：后台 startActivity 会被 `backgroundActivityAllowed=false` 拦，
 * 且会亮屏、打断用户正在用的应用。
 *
 * ## 权限
 *
 * - Android 12+ 精确闹钟需要 `SCHEDULE_EXACT_ALARM`。没拿到时退化成
 *   [AlarmManager.setAndAllowWhileIdle]（不精确，doze 下最多晚十几分钟，够用）。
 */
object DailyAlarm {
    private const val REQUEST_CODE = 0x5A17

    const val ACTION_ALARM = "com.tiantong.spark.action.DAILY_ALARM"

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
        if (hour !in 0..23) {
            ScheduleGuardService.cancel(context)
            return
        }
        val at = nextTriggerAt(hour, minute)
        if (at <= 0L) {
            ScheduleGuardService.cancel(context)
            return
        }
        // 调度兜底：登记一个 15 分钟周期 JobScheduler，防单一 alarm 被厂商冻结/清掉后彻底不响
        ScheduleGuardService.schedule(context)

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
     * 拉起抖音，两条路：**先免 root，失败再回退 root + monkey**。
     *
     * 1. 免 root：`acquireUnstableContentProviderClient` 向抖音 preload provider 取 client，
     *    会触发抖音主进程启动，不需要 su（见 [tryPreload]）。
     * 2. 回退 root：`monkey -p <pkg> -c LAUNCHER 1` 走 AMS 注入通道，实测不点亮屏幕
     *    （`mWakefulness` 保持 Asleep）且能清掉 `stopped=true`；但它要求 root / shell 身份，
     *    普通 App 身份跑会被 `Killed`（见 [RootShell.monkeyLaunch]）。
     *
     * 都不用 startActivity：Android 10+ 的 `backgroundActivityAllowed=false` 会拦后台
     * startActivity，且会亮屏、打断用户正在用的应用，不可靠。
     *
     * @return 用了哪条路，用于日志对账。
     */
    fun launchDouyin(context: Context): LaunchMethod {
        // 1) 先试免 root：向抖音 preload provider 取 client 会拉起抖音主进程，不需要 su
        if (tryPreload(context)) return LaunchMethod.PRELOAD
        // 2) 免 root 失败再回退 root + monkey（不亮屏）
        return RootShell.monkeyLaunch(DOUYIN_PACKAGE) ?: LaunchMethod.FAILED
    }

    /**
     * 免 root 拉起：`acquireUnstableContentProviderClient` 会触发目标进程（抖音）启动。
     * provider 未导出 / 不存在时抛异常或返回 null，一律当作失败，交给 root 兜底。
     */
    private fun tryPreload(context: Context): Boolean =
        runCatching {
            val uri = Uri.parse("content://$DOUYIN_PACKAGE.depths.pre.load")
            val client = context.contentResolver.acquireUnstableContentProviderClient(uri) ?: return@runCatching false
            runCatching { client.close() }
            true
        }.getOrDefault(false)

    /** 把抖音关掉。 */
    fun stopDouyin(): Boolean = RootShell.amKill(DOUYIN_PACKAGE)

    /** 拉起方式，用于日志与界面提示。 */
    enum class LaunchMethod {
        /** root + monkey，不亮屏 */
        MONKEY,

        /** 免 root：preload provider 拉起抖音主进程 */
        PRELOAD,

        /** 没成功（preload 与 root 都没成 / 抖音没装） */
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
