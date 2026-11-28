package com.tiantong.spark.app.alarm

import android.content.Context
import com.tiantong.spark.app.config.ConfigStore
import com.tiantong.spark.app.config.SparkConfig

/**
 * 定时唤醒历史。
 *
 * 每次 [AlarmReceiver] 触发闹钟、拉起抖音后，把 [DailyAlarm.launchDouyin] 的结果
 * （PRELOAD / MONKEY / FAILED）追加一条到 [SparkConfig.wakeLog]，设置页「唤醒日志」
 * 用它展示「唤醒了多少次、每次用的哪种方式」。
 *
 * 每行格式 `时间戳毫秒|方式名`，只保留最近 [MAX] 条。
 */
object WakeLog {
    private const val MAX = 30
    private const val SEP = "|"

    data class Entry(val at: Long, val method: DailyAlarm.LaunchMethod)

    /** 记一条唤醒结果。写配置失败不影响主流程。 */
    fun record(context: Context, method: DailyAlarm.LaunchMethod) {
        runCatching {
            ConfigStore.update(context) { c ->
                val line = System.currentTimeMillis().toString() + SEP + method.name
                val lines = (c.wakeLog.split('\n').filter { it.isNotBlank() } + line).takeLast(MAX)
                c.copy(wakeLog = lines.joinToString("\n"))
            }
        }
    }

    /** 解析配置里的历史（按记录顺序，旧→新）。 */
    fun entries(config: SparkConfig): List<Entry> =
        config.wakeLog.split('\n').filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split(SEP)
            val at = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val name = parts.getOrNull(1) ?: return@mapNotNull null
            val method = DailyAlarm.LaunchMethod.values().firstOrNull { it.name == name } ?: return@mapNotNull null
            Entry(at, method)
        }
}
