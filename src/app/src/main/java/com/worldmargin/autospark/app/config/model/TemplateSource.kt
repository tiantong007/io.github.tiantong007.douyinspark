package com.worldmargin.autospark.app.config.model

/** 会话 / 模板可以继承的来源（内置模板、自定义模板、派生、克隆）。 */
data class TemplateSource(
    val parentId: String?,
    val name: String,
    val body: String,
)

/**
 * 会话快照：由抖音进程写入，供管理界面展示。
 *
 * 同时也是「预览用的抖音侧变量缓存」：昵称/备注/头像/天数/状态等都缓存下来，
 * 预览时直接用缓存值，不会因为缺变量而报错。
 *
 * 火花字段语义（来自 `a:consecutive_chat_data.flame_infos` 的时间轴，取覆盖当前时刻的那段）：
 * [state] `1` = 今天已互动（火苗亮）、`2` = 今天还没互动（火苗灰，今天必须发）、
 * `3` = 待重燃（已断，靠连续互发 [recoverNeedDays] 天恢复，[recoverDays] 是已恢复进度）、
 * `-1` / [hasSpark] 为 false = 没有火苗。
 */
