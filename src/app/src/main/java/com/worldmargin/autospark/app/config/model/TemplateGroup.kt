package com.worldmargin.autospark.app.config.model

import com.worldmargin.autospark.app.config.Hash
/** 自定义模板（一级模板，可被会话继承）。 */
data class TemplateGroup(
    val id: String,
    val name: String,
    val body: String,
    val updatedAt: Long,
    val variables: List<TemplateVar> = emptyList(),
) {
    val bodyHash: String get() = Hash.of(body)
}
