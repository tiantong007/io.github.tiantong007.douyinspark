package com.tiantong.spark.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.tiantong.spark.app.config.ConfigStore
import com.tiantong.spark.app.config.SparkConfig

/**
 * 定时闹钟 / 补发闹钟 / 开机完成时被叫醒。只做四件事：
 * **记录运行前状态、静音（可选）、拉起抖音、发完按设置收尾**。
 *
 * 本接收器跑在**模块进程**，真正发消息的是抖音进程里的 AutoSparkBridge。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        val app = context?.applicationContext ?: return
        val config = runCatching { ConfigStore.load(app) }.getOrNull() ?: return
        val action = intent?.action

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // 重启 / 覆盖安装后闹钟会被系统清掉，重排一次
            if (config.alarmHour in 0..23) {
                DailyAlarm.schedule(app, config.alarmHour, config.alarmMinute)
            }
            return
        }

        val isRetry = action == DailyAlarm.ACTION_RETRY
        if (action != DailyAlarm.ACTION_ALARM && !isRetry) return
        if (!isRetry && config.alarmHour !in 0..23) return

        // 一次性主闹钟：响完立刻排下一天（补发闹钟不排）
        if (!isRetry) DailyAlarm.schedule(app, config.alarmHour, config.alarmMinute)

        // 补发轮：先置 pending，抖音进程 Application.attach 时据此跑补发（而不是普通检查）
        if (isRetry) {
            runCatching { ConfigStore.update(app) { it.copy(retryPending = true) } }
        }

        // 记录「运行前状态」，用于任务后 restore（回到运行前所在应用）
        val prevPkg = RootShell.foregroundPackage()
        val wasAwake = RootShell.screenAwake()

        // 静音（可选）
        if (config.muteDuringRun) VolumeGuard.mute(app)

        holdProcess(app)
        val baseline = RootShell.modifiedAt(DAY_FILE)
        val method = DailyAlarm.launchDouyin(app)
        WakeLog.record(app, method)
        Log.i(TAG, "闹钟触发(isRetry=$isRetry)：拉起抖音 $method，基线 mtime=$baseline")
        if (method == DailyAlarm.LaunchMethod.FAILED) {
            if (config.muteDuringRun) VolumeGuard.restore(app)
            return
        }
        watchAndClose(app, baseline, config, prevPkg, wasAwake)
    }

    /** 申请带超时的 WakeLock，覆盖「抖音冷启动 → IM 就绪 → 发送」这段。 */
    private fun holdProcess(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            manager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "douyinspark:alarm")
                .apply {
                    setReferenceCounted(false)
                    acquire(WATCH_TIMEOUT_MS)
                }
        }
    }

    /**
     * 等抖音发完，然后收尾。判据是 `spark_last_auto_day` 的 mtime 在本次闹钟之后变大
     * （内容早已是今天，不能用内容判断，否则会在冷启动中途把抖音杀掉）。
     */
    private fun watchAndClose(
        app: Context,
        baseline: Long,
        config: SparkConfig,
        prevPkg: String?,
        wasAwake: Boolean,
    ) {
        Thread(
            {
                val deadline = System.currentTimeMillis() + WATCH_TIMEOUT_MS
                var finished = false
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(POLL_INTERVAL_MS)
                    if (RootShell.modifiedAt(DAY_FILE) > baseline) {
                        Thread.sleep(SETTLE_MS) // 让 markSent 落完盘
                        finished = true
                        break
                    }
                }
                if (!finished) {
                    Log.i(TAG, "等了 ${WATCH_TIMEOUT_MS / 1000} 秒没等到新一轮记账，仍然收尾")
                }
                finishRun(app, config, prevPkg, wasAwake)
            },
            "spark-close-watch",
        ).apply { isDaemon = true }.start()
    }

    private fun finishRun(
        app: Context,
        config: SparkConfig,
        prevPkg: String?,
        wasAwake: Boolean,
    ) {
        if (config.muteDuringRun) VolumeGuard.restore(app)
        applyAfterRun(app, config, prevPkg, wasAwake)
        // 主 / 补发这一轮结束后，按需安排下一次补发（内部判断是否还有轮数）
        runCatching { RetryPlanner.afterRun(app) }
    }

    private fun applyAfterRun(
        app: Context,
        config: SparkConfig,
        prevPkg: String?,
        wasAwake: Boolean,
    ) {
        when (config.afterRunAction) {
            "keep" -> Unit
            "kill" -> DailyAlarm.stopDouyin()
            else -> { // restore：回到运行前所在的应用
                DailyAlarm.stopDouyin()
                if (wasAwake) {
                    val p = prevPkg
                    if (p != null && p != DOUYIN && p != app.packageName) RootShell.launchPackage(p)
                }
                // 运行前是息屏的，就保持息屏（不点亮别的应用）
            }
        }
    }

    private companion object {
        const val TAG = "SparkAlarm"
        const val DOUYIN = "com.ss.android.ugc.aweme"
        val DAY_FILE = RootShell.HOST_FILES_DIR + "/spark_last_auto_day"
        const val WATCH_TIMEOUT_MS = 300_000L
        const val POLL_INTERVAL_MS = 10_000L
        const val SETTLE_MS = 3_000L
    }
}
