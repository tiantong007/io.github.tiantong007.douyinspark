package com.worldmargin.autospark.app.script

import com.worldmargin.autospark.core.ModLog
import org.mozilla.javascript.ScriptRuntime
import java.text.MessageFormat
import java.util.Locale
import java.util.Properties

/**
 * 接管 Rhino 的错误文案来源。
 *
 * Rhino 报错时在 `ScriptRuntime.getMessageById` 里查 `org.mozilla.javascript.resources.Messages`
 * 资源包（键形如 `msg.undef.prop.read`）。打包进 APK、跑在应用进程里时这份资源包偶尔查不到，
 * 于是**任何**脚本错误都变成一句
 * `Can't find bundle for base name org.mozilla.javascript.resources.Messages, locale zh_CN`
 * —— 真正的原因被这句话盖掉，用户和开发者都看不出脚本哪里写错了。
 *
 * 这里用反射把 `ScriptRuntime.messageProvider`（`public static final`）换成自己的实现：
 * 自己从 classloader 读同一份 `Messages.properties`（键与模板都保持 Rhino 原样），
 * 读不到就退化成 `[msg.xxx]` 这种可读标记。**取代的是「查资源包」这一步，不是文案本身**，
 * 所以正常环境下的输出与官方一致（`missing variable name (s#1)` 之类）。
 *
 * 只在 [SparkScriptEngine.renderFull] 里调一次，失败只记日志、绝不影响脚本执行。
 */
internal object RhinoMessages {
    /** Rhino 资源包基名（键 294 条，文件在 rhino jar 里）。 */
    private const val BASE = "org/mozilla/javascript/resources/Messages"

    private const val FIELD_NAME = "messageProvider"

    @Volatile
    private var installed = false

    /** 幂等：装一次就够，进程内共用。 */
    fun install() {
        if (installed) return
        runCatching {
            val props = Properties()
            RhinoMessages::class.java.classLoader
                ?.getResourceAsStream("$BASE.properties")
                ?.use { props.load(it) }
            val field = ScriptRuntime::class.java.getField(FIELD_NAME)
            field.isAccessible = true
            field.set(null, Provider(props))
            installed = true
            ModLog.i("Rhino 错误文案已接管（${props.size} 条）")
        }.onFailure {
            ModLog.w("Rhino 错误文案接管失败（脚本报错信息可能仍是资源包提示）", it)
        }
    }

    /**
     * 与 Rhino `DefaultMessageProvider` 同口径：`Messages.properties` 的模板 + `MessageFormat`。
     *
     * 区别只有两处，都是为了「别再抛二次异常」：
     * - 查不到键不抛 `NullPointerException` / `MissingResourceException`，直接回 `[msg.xxx]`；
     * - 模板本身格式化失败（占位符与参数个数不符）就回原模板，不抛 `IllegalArgumentException`。
     */
    private class Provider(private val props: Properties) : ScriptRuntime.MessageProvider {
        override fun getMessage(id: String?, args: Array<out Any?>?): String {
            val key = id.orEmpty()
            val pattern = props.getProperty(key) ?: return fallback(key, args)
            val values = args ?: emptyArray()
            return runCatching {
                MessageFormat(pattern, Locale.ROOT).format(values)
            }.getOrElse { pattern }
        }

        private fun fallback(
            key: String,
            args: Array<out Any?>?,
        ): String {
            if (args.isNullOrEmpty()) return key
            return key + args.joinToString(prefix = "(", postfix = ")", separator = ", ") { it?.toString() ?: "null" }
        }
    }
}
