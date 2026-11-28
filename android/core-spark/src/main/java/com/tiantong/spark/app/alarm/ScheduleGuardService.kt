package com.tiantong.spark.app.alarm

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.tiantong.spark.app.config.ConfigStore

/**
 * 调度兜底：一个 15 分钟周期的 JobScheduler，作用是**把一次性闹钟重新排上**。
 *
 * ## 为什么需要它
 *
 * 主路径是 [DailyAlarm] 的 `setExactAndAllowWhileIdle`，但一次性 Exact 闹钟在国产 ROM 上
 * 很容易被「省电策略 / 后台冻结 / 清理」连带清掉，一次丢失就整天不响。JobScheduler 由系统
 * 统一调度、可 `setPersisted`（重启/覆盖安装后仍保留并补跑），作为第二道保险：只要它跑一次，
 * 就把 [DailyAlarm.schedule] 重新调一遍，闹钟就算被清掉也会被补回来。
 *
 * ## 为什么这里**不**直接发消息 / 拉抖音
 *
 * 兜底绝不能和主闹钟抢着发——两边都拉抖音会重复发送。所以这个 Job **只重排闹钟、绝不触发发送**。
 * 真正到点干活仍只由 [AlarmReceiver] 在闹钟响时做。
 */
class ScheduleGuardService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        runCatching {
            val config = ConfigStore.load(applicationContext)
            if (config.alarmHour in 0..23) {
                // 只重排，不触发发送（见类注释：避免与主闹钟重复发）
                DailyAlarm.schedule(applicationContext, config.alarmHour, config.alarmMinute)
            }
        }.onFailure { Log.w(TAG, "调度兜底重排闹钟失败", it) }
        // 没有异步工作，直接结束
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val TAG = "SparkJobGuard"

        /** 15 分钟是 JobScheduler 允许的最小周期。 */
        private const val PERIOD_MS = 15 * 60 * 1000L

        private const val JOB_ID = 0x5A18

        /** 登记（或更新）兜底 Job。幂等，重复调用只会刷新同一个 job。 */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val info =
                JobInfo
                    .Builder(JOB_ID, ComponentName(context, ScheduleGuardService::class.java))
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true) // 重启 / 覆盖安装后仍在（需要 RECEIVE_BOOT_COMPLETED 权限，清单已有）
                    .build()
            runCatching { scheduler.schedule(info) }
                .onFailure { Log.w(TAG, "登记兜底 Job 失败", it) }
        }

        /** 关闭定时唤醒时取消兜底 Job。 */
        fun cancel(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            runCatching { scheduler.cancel(JOB_ID) }
        }
    }
}
