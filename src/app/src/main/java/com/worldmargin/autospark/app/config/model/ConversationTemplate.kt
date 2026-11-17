package com.worldmargin.autospark.app.config.model

import com.worldmargin.autospark.app.config.Hash
import com.worldmargin.autospark.app.config.SparkConfig
/** 会话的文案模板（继承某个一级模板，或自己写）。 */
data class ConversationTemplate(
    val conversationId: String,
    val inheritFrom: String?,
    val body: String,
    val parentHashAtSync: String,
    val autoSync: Boolean,
    val updatedAt: Long,
    val selectedAt: Long = 0L,
    val variables: List<TemplateVar> = emptyList(),
) {
    /**
     * 已修改 = 自身内容与「上次同步到的父内容」不一致。
     *
     * 空白模板（`builtin:blank`）不算：它的内容本来就是空的，自己写的每一句都"不等于父模板"，
     * 但那是正常用法，不该标成「已本地修改」。
     */
    val modified: Boolean
        get() = inheritFrom != null && inheritFrom != SparkConfig.BUILTIN_BLANK && Hash.of(body) != parentHashAtSync
}
