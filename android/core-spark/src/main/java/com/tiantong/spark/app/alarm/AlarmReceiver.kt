package com.tiantong.spark.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.tiantong.spark.app.config.ConfigStore

/**
 * 定时闹钟 / 开机完成时被叫醒。
 *
 * 只做三件事：**拉起抖音**、**排下一天的闹钟**、**发完把抖音关掉**。
 *
 * 本接收器跑在**模块进程**，不在抖音进程；真正发消息的是抖音进程里的
 * [com.tiantong.spark.host.bridge.AutoSparkBridge]，它靠
 * `Application.attach` 的 hook 自动启动。
 *
 * ## 为什么闹钟能自救
 *
 * 这个 PendingIntent 是**显式广播**（`Intent(context, AlarmReceiver::class.java)`），
 * 所以哪怕模块进程已经被系统回收，闹钟响时系统也会 fork 一个新进程执行 [onReceive]。
 * 模块进程一活，`ConfigProvider` 就可达，抖音那边才读得到配置。
 *
 * 只有 `force-stop`（手动一键清理 / 上滑清理）会同时清掉闹钟，那种情况需要手动开一次 App。
 *
 * ## 为什么关闭动作放在这里而不是抖音进程里
 *
 * 「发完杀抖音」如果写在抖音进程内，就是进程自己杀自己：`markRunDay` 落盘和
 * `killProcess` 之间有竞态，容易丢记录导致第二天重发。放在模块进程里做，
 * 判据是「`spark_last_auto_day` 在本次闹钟之后被重新写过」（比 mtime）这个
 * **已落盘的事实**，没有竞态。
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
        if (action != DailyAlarm.ACTION_ALARM) return
        if (config.alarmHour !in 0..23) return

        // 一次性闹钟：响完立刻排下一天（不依赖抖音是否启动成功）
        DailyAlarm.schedule(app, config.alarmHour, config.alarmMinute)

        // 抖音冷启动 + IM 就绪大约要 20 秒。onReceive 一返回进程优先级就掉到底，
        // 必须自己撑着，否则抖音去读配置时模块已经没了。
        holdProcess(app)
        // 先记下「今天已跑过」标记文件的 mtime，再拉起抖音。
        // 后面的判据是「它变大了」= 本轮真的跑完，而不是「内容等于今天」（早就是今天了）。
        val baseline = RootShell.modifiedAt(DAY_FILE)
        val method = DailyAlarm.launchDouyin(app)
        Log.i(TAG, "闹钟触发：拉起抖音 $method，基线 mtime=$baseline，开始等发送完成")
        if (method == DailyAlarm.LaunchMethod.FAILED) return
        watchAndClose(baseline)
    }

    /**
     * 申请一个带超时的 WakeLock，覆盖「抖音冷启动 → IM 就绪 → 发送」这段。
     *
     * 不用 `acquire()` 无参版本：那是硬持锁，漏释放会持续耗电。这里给 5 分钟兜底，
     * 和 [watchAndClose] 的轮询窗口对齐。
     */
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
     * 等抖音发完，然后关掉。
     *
     * 判据不是「函数返回了」，也不是「文件内容等于今天」——**内容早就是今天了**：
     * 同一自然日里抖音被打开过一轮就会写这个文件，闹钟再响一次时它已经等于今天，
     * 拿内容做判据会在第一次轮询（10 秒）就把还在冷启动的抖音杀掉（实测抖音
     * 只活 12.9 秒、一条都没发）。
     *
     * 正确判据是 **文件在本次闹钟响了之后被重新写过**：记下响铃前的 mtime，
     * 等它变大。文件是 [com.tiantong.spark.host.bridge.AutoSparkBridge]
     * 在 `runRenew` 走完后才写的，所以 mtime 变了才说明这一轮真的跑完了。
     *
     * @param baseline 闹钟响之前那个文件的 mtime（毫秒），见 [DailyAlarm] 的调用点。
     */
    private fun watchAndClose(baseline: Long) {
        Thread(
            {
                val deadline = System.currentTimeMillis() + WATCH_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(POLL_INTERVAL_MS)
                    val now = RootShell.modifiedAt(DAY_FILE)
                    if (now > baseline) {
                        // 提前退出：mtime 变了就说明这一轮真的跑完了，不必等满超时。
                        // 再等几秒：markSent 与 markRunDay 是两次独立写入
                        Thread.sleep(SETTLE_MS)
                        DailyAlarm.stopDouyin()
                        return@Thread
                    }
                }
                // 超时也关：抖音是这一轮 monkey 拉起来的，不是用户主动开的，留着只占内存。
                // 不关的话它会一直停在界面上，下次用户解锁就可能看到。
                Log.i(TAG, "等了 ${WATCH_TIMEOUT_MS / 1000} 秒没等到新一轮记账，仍然关掉抖音")
                DailyAlarm.stopDouyin()
            },
            "spark-close-watch",
        ).apply { isDaemon = true }.start()
    }

    private companion object {
        const val TAG = "SparkAlarm"

        /** 抖音进程写的「今天已跑过」标记文件。判据是它的 mtime 在本次闹钟之后变大。 */
        val DAY_FILE = RootShell.HOST_FILES_DIR + "/spark_last_auto_day"

        /**
         * 从闹钟响到判定「发完了」的最长等待。
         *
         * 冷启动全程约 20 秒，加上 IM 首轮探测（每个单聊查一次对方资料，最坏 3 秒超时）
         * 与逐个发送，3 分钟会在最坏情况下中途放弃、把还在发消息的抖音 kill 掉。
         * 放宽到 5 分钟；真发完了会被轮询发现并提前退出，不会白等。
         */
        const val WATCH_TIMEOUT_MS = 300_000L

        /** 轮询间隔。10 秒足够细，又不会把 CPU 叫醒太频繁。 */
        const val POLL_INTERVAL_MS = 10_000L

        /** 检测到「今天」之后再等多久才动手杀 —— 让 markSent 落完盘。 */
        const val SETTLE_MS = 3_000L
    }
}