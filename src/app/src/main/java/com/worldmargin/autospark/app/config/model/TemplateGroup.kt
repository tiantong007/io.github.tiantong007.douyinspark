package com.worldmargin.autospark.app.config.model

import org.json.JSONArray
import org.json.JSONObject
import com.worldmargin.autospark.app.config.Hash
import com.worldmargin.autospark.app.config.SparkConfig
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

/**
 * 会话模板。
 *
 * - [inheritFrom] 指向「跟随谁」：某个通用模板 [TemplateGroup.id]、内置模板（`builtin:xxx`，
 *   含 `builtin:blank` = **空白模板**，即"不用模板、自己写"；克隆来的也归空白模板）。
 * - [parentHashAtSync] 记录上次同步时父模板 body 的哈希，
 *   用于判定本模板是否被本地改过（[modified]）。
 * - [selectedAt] 最后一次出现在「已选目标」里的时间；不在已选目标里的会话，
 *   从这一刻起算满 [SparkConfig.TEMPLATE_GRACE_MS] 就清掉它的模板与发送记录。
 * - [variables] 这个会话**自己的**变量：生效时排在父模板变量之后（同名覆盖父模板），
 *   所以能引用父模板的变量；父模板同步内容时不会动它。
 */
