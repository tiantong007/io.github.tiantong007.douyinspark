package com.worldmargin.autospark.app.config.model

/** 沙盒变量：脚本通过 `ctx.sandbox.setvar` 写下的数据。 */
data class SandboxVar(
    val owner: String,
    val key: String,
    val value: String,
)
