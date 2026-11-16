package com.worldmargin.autospark.app.config.model

/** 会话标签：纯展示 / 筛选用，不参与发送决策。 */
data class SparkTag(
    val id: String,
    val name: String,
    val color: Int = 0,
    val createdAt: Long = 0L,
)

