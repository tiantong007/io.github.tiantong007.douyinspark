package com.worldmargin.autospark.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.worldmargin.autospark.app.config.ConfigStore

/**
 * 定时闹钟 / 开机完成时被叫醒。
 *
 * 只有两件事：**拉起抖音**，**把明天的闹钟排上**。
 *
 * 注意本接收器跑在**模块进程**，不在抖音进程；真正发消息的是抖音进程里的
 * [com.worldmargin.autospark.host.bridge.AutoSparkBridge]，它靠
 * `Application.attach` 的 hook 自动启动。
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

        DailyAlarm.launchDouyin(app)
        // 一次性闹钟：响完立刻排下一天（不依赖抖音是否启动成功）
        DailyAlarm.schedule(app, config.alarmHour, config.alarmMinute)
    }
}
