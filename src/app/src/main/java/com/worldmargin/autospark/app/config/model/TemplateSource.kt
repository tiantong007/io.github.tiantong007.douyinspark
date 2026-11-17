package com.worldmargin.autospark.app.config.model

/** 会话 / 模板可以继承的来源（内置模板、自定义模板、派生、克隆）。 */
data class TemplateSource(
    val parentId: String?,
    val name: String,
    val body: String,
)
