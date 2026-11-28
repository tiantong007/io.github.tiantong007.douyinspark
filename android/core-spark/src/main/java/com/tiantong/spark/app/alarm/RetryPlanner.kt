package com.tiantong.spark.app.alarm

import android.content.Context
import com.tiantong.spark.app.config.ConfigStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 待燃补发的排程器。主发送 / 每一轮补发跑完后调用 [afterRun]：
 * 如果开了补发且当天还有轮数，就在 `start + round * interval` 处安排下一次补发闹钟。
 *
 * 轮数按天记账（`retryDay` 换天自动归零），补发本身不发送新内容 —— 到点只拉起抖音，
 * 由抖音进程里的 HostProbe 走「补发分支」，对**仍未点亮**的会话重复发同一条文案。
 */
internal object RetryPlanner {
    private fun dayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun afterRun(context: Context) {
        runCatching {
            val config = ConfigStore.load(context)
            val today = dayStr()
            if (!config.retryEnabled || config.retryMaxRounds <= 0) {
                ConfigStore.update(context) { it.copy(retryPending = false) }
                return
            }
            val round = if (config.retryDay == today) config.retryRound else 0
            if (round >= config.retryMaxRounds) {
                // 今天补满了：清掉 pending，明天主闹钟重新开始
                ConfigStore.update(context) { it.copy(retryDay = today, retryRound = round, retryPending = false) }
                return
            }
            val nextRound = round + 1
            val at = nextAt(config.retryStartHour, config.retryStartMinute, config.retryIntervalMin, nextRound)
            if (at <= 0L) {
                ConfigStore.update(context) { it.copy(retryDay = today, retryRound = round, retryPending = false) }
                return
            }
            ConfigStore.update(context) { it.copy(retryDay = today, retryRound = nextRound) }
            DailyAlarm.scheduleRetryAt(context, at)
        }
    }

    /** 计算第 [round] 轮补发的时刻；跨越到明天或间隔非法返回 -1。 */
    private fun nextAt(
        startHour: Int,
        startMinute: Int,
        intervalMin: Int,
        round: Int,
    ): Long {
        val step = intervalMin.coerceAtLeast(1).toLong() * 60_000L
        val now = System.currentTimeMillis()
        val at: Long =
            if (startHour in 0..23) {
                val c =
                    Calendar.getInstance().apply {
                        timeInMillis = now
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, startMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                var t = c.timeInMillis + (round - 1) * step
                if (t < now) {
                    val k = (now - t) / step + 1
                    t += k * step
                }
                t
            } else {
                now + step
            }
        val dayOfAt = Calendar.getInstance().apply { timeInMillis = at }.get(Calendar.DAY_OF_YEAR)
        val dayOfNow = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_YEAR)
        return if (dayOfAt != dayOfNow) -1L else at
    }
}
