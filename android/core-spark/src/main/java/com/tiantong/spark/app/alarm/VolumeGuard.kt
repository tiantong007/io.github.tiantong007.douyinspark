package com.tiantong.spark.app.alarm

import android.content.Context
import android.media.AudioManager

/**
 * 运行期间静音：跑之前把媒体 / 铃声 / 通知 / 闹钟 / 系统音量存起来再降到 0，跑完恢复。
 *
 * 原音量存进模块进程自己的 SharedPreferences，和业务配置完全隔离；哪怕被杀进程 / 崩溃，
 * App 下次启动时调一次 [restore] 也能把音量还原，避免手机被永久静音。
 */
internal object VolumeGuard {
    private const val PREFS = "volume_guard"
    private const val KEY = "saved"

    private val STREAMS =
        intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_SYSTEM,
        )

    fun mute(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (sp.getString(KEY, null) != null) return // 已经存过，别把 0 当原音量
            val map = STREAMS.associateWith { am.getStreamVolume(it) }
            sp.edit().putString(KEY, map.entries.joinToString(",") { "${it.key}=${it.value}" }).apply()
            STREAMS.forEach { runCatching { am.setStreamVolume(it, 0, 0) } }
        }
    }

    fun restore(context: Context) {
        runCatching {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val text = sp.getString(KEY, null) ?: return
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            text.split(",").forEach { part ->
                val kv = part.split("=")
                if (kv.size == 2) {
                    val stream = kv[0].toIntOrNull()
                    val vol = kv[1].toIntOrNull()
                    if (stream != null && vol != null) runCatching { am.setStreamVolume(stream, vol, 0) }
                }
            }
            sp.edit().remove(KEY).apply()
        }
    }
}
