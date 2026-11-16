package com.worldmargin.autospark.app.config.model

import org.json.JSONArray
import org.json.JSONObject
import com.worldmargin.autospark.app.config.SparkConfig
/** 沙盒变量：脚本通过 `ctx.sandbox.setvar` 写下的数据。 */
data class SandboxVar(
    val owner: String,
    val key: String,
    val value: String,
)

/**
 * 会话标签：纯展示 / 筛选用，不参与发送决策。
 *
 * [color] 是调色板下标（见 [SparkConfig.TAG_COLORS]），不是 ARGB —— 存下标跨版本换配色更方便。
 */
