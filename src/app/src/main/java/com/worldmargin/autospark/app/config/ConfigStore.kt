package com.worldmargin.autospark.app.config

import android.content.Context
import com.worldmargin.autospark.app.config.ConfigCodec
import com.worldmargin.autospark.app.config.SparkConfig
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import com.worldmargin.autospark.app.provider.ConfigProvider

/**
 * 配置文件存储：单一来源，存在模块 App 的私有目录。
 *
 * 仅由模块进程（管理界面 / [com.worldmargin.autospark.app.provider.ConfigProvider]）访问；
 * 抖音进程通过 ContentProvider 跨进程读写。
 *
 * 内存缓存按**文件修改时间**失效：配置文件可能被外部改掉（备份还原、root 下直接改、
 * 调试时用 adb 写入），只看进程内缓存会读到旧内容、再把旧内容写回去。
 *
 * **损坏保护（cc4 起）**：[ConfigCodec.decodeOrNull] 返回 null 表示文件 JSON 不合法
 * （典型场景：安装 APK 时进程被杀，非原子写只落了半截内容）。此时：
 * 1. 把损坏文件另存为 `spark_config.json.broken-<时间戳>`；
 * 2. 置 [broken] 标记；
 * 3. [update] 在标记置位时**拒绝写入**并抛异常，避免用空配置覆盖用户数据。
 */
object ConfigStore {
    private const val FILE_NAME = "spark_config.json"

    private class Cached(
        val config: SparkConfig,
        val stamp: Long,
        val size: Long,
    )

    private val cache = AtomicReference<Cached?>(null)

    /** 最近一次 load 是否遇到「文件存在但解析失败」。 */
    @Volatile
    private var broken = false

    /** 最近一次备份的损坏文件路径（给界面提示用）。 */
    @Volatile
    var brokenBackupPath: String? = null
        private set

    @Synchronized
    fun load(context: Context): SparkConfig {
        val file = File(context.filesDir, FILE_NAME)
        val stamp = if (file.exists()) file.lastModified() else NO_FILE
        val size = if (file.exists()) file.length() else NO_FILE
        val cached = cache.get()
        // 修改时间精度只有秒级：同一秒内的外部改动要靠文件长度一起判断。
        if (cached != null && cached.stamp == stamp && cached.size == size && !broken) return cached.config

        if (!file.exists()) {
            broken = false
            val fresh = SparkConfig()
            cache.set(Cached(fresh, stamp, size))
            return fresh
        }

        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
        val decoded = text?.let { runCatching { ConfigCodec.decodeOrNull(it) }.getOrNull() }
        if (decoded != null) {
            broken = false
            brokenBackupPath = null
            cache.set(Cached(decoded, stamp, size))
            return decoded
        }

        // 解析失败：先备份现场，再返回缓存（绝不返回「空配置 + 允许写回」的组合）
        broken = true
        if (brokenBackupPath == null) {
            runCatching {
                val backup = File(context.filesDir, "$FILE_NAME.broken-${System.currentTimeMillis()}")
                file.copyTo(backup, overwrite = true)
                brokenBackupPath = backup.absolutePath
            }
        }
        cached?.let { return it.config }
        return SparkConfig()
    }

    @Synchronized
    fun save(
        context: Context,
        config: SparkConfig,
    ) {
        val file = File(context.filesDir, FILE_NAME)
        // 原子写：先写临时文件再改名，避免读到半截内容
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.writeText(ConfigCodec.encode(config), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            // rename 失败（跨分区/占用）时用「写临时文件 + 再 rename」重试一次，仍失败才退化为直接写
            runCatching {
                if (file.exists()) file.delete()
                if (temp.renameTo(file)) return@runCatching
                file.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            }.onFailure { throw it }
            temp.delete()
        }
        broken = false
        cache.set(Cached(config, file.lastModified(), file.length()))
    }

    /**
     * 读改写，返回新配置。
     *
     * @throws IllegalStateException 配置文件损坏时抛出（此时已把损坏文件备份，调用方应提示用户恢复）
     */
    @Synchronized
    fun update(
        context: Context,
        block: (SparkConfig) -> SparkConfig,
    ): SparkConfig {
        val base = load(context)
        if (broken) {
            throw IllegalStateException(
                "配置文件损坏，已备份到 " + (brokenBackupPath ?: "spark_config.json.broken-*") +
                    "，为避免覆盖数据本次不保存",
            )
        }
        val next = block(base)
        save(context, next)
        return next
    }

    /** 文件不存在时的缓存标记。 */
    private const val NO_FILE = -1L
}
