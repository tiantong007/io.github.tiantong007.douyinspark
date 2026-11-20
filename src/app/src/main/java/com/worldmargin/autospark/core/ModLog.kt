package com.worldmargin.autospark.core

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * 统一日志出口。
 *
 * 优先走 libxposed 通道（模块日志，用户能在 LSPosed 里看到）；没有通道时回落到
 * `android.util.Log`。回落是必需的：设置页跑在模块自己的 App 进程里，那个进程永远不会
 * 调 [bind]（只有注入到抖音进程的 [com.worldmargin.autospark.ModuleMain] 会），
 * 只走通道的话界面里的自校验结果会全部丢掉，用户查不到。
 */
object ModLog {
    private const val TAG = "SparkFlame"

    @Volatile
    private var xposed: XposedInterface? = null

    /** 入口时机绑定（onModuleLoaded 调用一次） */
    fun bind(module: XposedInterface) {
        xposed = module
    }

    fun i(message: String) = dispatch(Log.INFO, message, null)

    fun w(message: String) = dispatch(Log.WARN, message, null)

    fun w(
        message: String,
        error: Throwable,
    ) = dispatch(Log.WARN, message, error)

    fun e(message: String) = dispatch(Log.ERROR, message, null)

    fun e(
        message: String,
        error: Throwable,
    ) = dispatch(Log.ERROR, message, error)

    private fun dispatch(
        priority: Int,
        message: String,
        error: Throwable?,
    ) {
        val module = xposed
        if (module != null) {
            if (error != null) {
                module.log(priority, TAG, message, error)
            } else {
                module.log(priority, TAG, message)
            }
        } else {
            // 模块 App 自己的进程（设置页、模板编辑页、定时唤醒）没有 libxposed 通道
            if (error != null) {
                Log.println(priority, TAG, message + "\n" + Log.getStackTraceString(error))
            } else {
                Log.println(priority, TAG, message)
            }
        }
    }
}
