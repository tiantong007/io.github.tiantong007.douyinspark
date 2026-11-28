package com.tiantong.spark.app.alarm

/**
 * 用 root 跑系统命令。
 *
 * 只服务于「把抖音叫起来 / 关掉」这两件事，不做别的。所有失败都返回 null，
 * 不抛异常 —— 拿不到 root 是正常状态，不是错误。
 *
 * ## 为什么必须 root
 *
 * `monkey` 只接受 root 或 shell 身份。普通应用身份执行会被系统直接 `Killed`：
 * ```
 * su 10276 -c "monkey -p ... 1"   → Killed
 * su 0     -c "monkey -p ... 1"   → Events injected: 1
 * ```
 *
 * ## 为什么用 monkey 而不是 am start
 *
 * `am start` 在后台会被 `backgroundActivityAllowed=false` 拦；`monkey` 走 AMS 的
 * 注入通道，实测能在**锁屏 + Doze**状态下把抖音拉起来且不点亮屏幕。
 */
internal object RootShell {
    private const val TIMEOUT_MS = 8_000L

    /** 有 root 吗（调一次 `id` 看是不是 uid=0）。结果缓存，避免每次都 fork。 */
    @Volatile
    private var rootChecked = false

    @Volatile
    private var rootAvailable = false

    fun hasRoot(): Boolean {
        if (rootChecked) return rootAvailable
        val out = exec("id")
        rootAvailable = out?.contains("uid=0") == true
        rootChecked = true
        return rootAvailable
    }

    /**
     * 用 monkey 拉起 [pkg]，成功返回 [DailyAlarm.LaunchMethod.MONKEY]，否则 null。
     *
     * @param pkg 目标包名。
     */
    fun monkeyLaunch(pkg: String): DailyAlarm.LaunchMethod? {
        if (!hasRoot()) return null
        val out = exec("/system/bin/monkey -p $pkg -c android.intent.category.LAUNCHER 1")
            ?: return null
        return if (out.contains("Events injected")) DailyAlarm.LaunchMethod.MONKEY else null
    }

    /**
     * 关掉 [pkg]。
     *
     * 用 `am force-stop` 而不是 `am kill`：后者只杀「可杀的后台进程」，
     * 而抖音被 monkey 拉起时是带 Activity 的前台进程，`am kill` 拿它没辙
     * （实测 `aweme procs` 仍是 2）。
     *
     * `force-stop` 会顺手设 `stopped=true`，但下次照样能用 monkey 拉起来
     * （monkey 走 AMS 注入通道，能清 stopped），所以不影响下一天。
     */
    fun amKill(pkg: String): Boolean {
        if (!hasRoot()) return false
        exec("/system/bin/am force-stop $pkg")
        // am force-stop 无输出，不能靠返回值判断；再查一次进程数
        val remain = exec("ps -A -o NAME | grep -c '^$pkg\$'")?.trim()?.toIntOrNull()
        return remain == 0
    }

    /** 当前前台应用的包名（运行前记录，用于任务后 restore 回到原应用）。取不到返回 null。 */
    fun foregroundPackage(): String? {
        if (!hasRoot()) return null
        val out =
            exec("/system/bin/dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'")
                ?: return null
        return Regex("([A-Za-z0-9_.]+)/[A-Za-z0-9_.]+").find(out)?.groupValues?.getOrNull(1)
    }

    /** 屏幕是否亮着（运行前若不是亮屏，跑完就不去点亮别的应用）。取不到按亮屏处理。 */
    fun screenAwake(): Boolean {
        if (!hasRoot()) return true
        val out = exec("/system/bin/dumpsys power | grep -E 'mWakefulness='") ?: return true
        return out.contains("Awake")
    }

    /** 把某个包拉到前台（restore 用）。成功返回 true。 */
    fun launchPackage(pkg: String): Boolean {
        if (!hasRoot()) return false
        val out = exec("/system/bin/monkey -p $pkg -c android.intent.category.LAUNCHER 1") ?: return false
        return out.contains("Events injected")
    }

    /** 宿主（抖音）私有目录的绝对路径。 */
    const val HOST_FILES_DIR = "/data/data/com.ss.android.ugc.aweme/files"

    /** 读一个文件的全部内容（走 root，不占 App 权限）。 */
    fun readText(path: String): String? {
        if (!hasRoot()) return null
        return exec("/system/bin/cat $path")
    }

    /** 文件的最后修改时间（毫秒），取不到返回 0。 */
    fun modifiedAt(path: String): Long {
        if (!hasRoot()) return 0L
        // toybox 的 `stat -c %Y` 给的是 epoch 秒
        val out = exec("/system/bin/stat -c %Y $path")?.trim() ?: return 0L
        return out.toLongOrNull()?.times(1000L) ?: 0L
    }

    /** 执行一条命令，返回 stdout+stderr；超时或异常返回 null。 */
    private fun exec(command: String): String? =
        runCatching {
            val process =
                ProcessBuilder("/system/bin/su", "-M", "-c", command)
                    .redirectErrorStream(true)
                    .start()
            val output = StringBuilder()
            val reader = process.inputStream.bufferedReader()
            val finished =
                Thread {
                    runCatching { reader.forEachLine { output.appendLine(it) } }
                }.apply { isDaemon = true }.also { it.start() }
                    .let { thread ->
                        thread.join(TIMEOUT_MS)
                        !thread.isAlive
                    }
            if (!finished) {
                runCatching { process.destroyForcibly() }
                return null
            }
            runCatching { process.waitFor() }
            output.toString()
        }.getOrNull()
}
