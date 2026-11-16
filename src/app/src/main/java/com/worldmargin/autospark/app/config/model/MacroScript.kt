package com.worldmargin.autospark.app.config.model

import org.json.JSONArray
import org.json.JSONObject
/** 宏：挂在模板 / 会话上的后置脚本。 */
data class MacroScript(
    val id: String,
    val name: String,
    val body: String,
    /** 生效目标：通用模板 id 或会话 id（两者命名空间不重叠）。 */
    val appliesTo: Set<String> = emptySet(),
    /**
     * 「仅模板」挂载：只在该模板自己的试算 / 预览里跑，
     * **不往派生会话传播**（传播的那种写在 [appliesTo] 里）。
     */
    val templateOnly: Set<String> = emptySet(),
    /** 脚本编辑器用 WebView；保留字段仅为兼容旧配置。 */
    val useMonacoEditor: Boolean = true,
    val updatedAt: Long = 0L,
)

/**
 * 用户自定义函数：每次执行脚本前注入成 `ctx.functions.<name>`，模板里可直接调用。
 *
 * [params] 是逗号分隔的形参名；[body] 是函数体（要返回值就写 return）。
 */
