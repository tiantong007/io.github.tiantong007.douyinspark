package com.worldmargin.autospark.app.config.model

/** 模板 / 会话变量：名字 + JS 表达式。 */
data class TemplateVar(
    val name: String,
    val expr: String,
)

