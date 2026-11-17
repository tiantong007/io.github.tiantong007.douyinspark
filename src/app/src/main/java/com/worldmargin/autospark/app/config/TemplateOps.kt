package com.worldmargin.autospark.app.config

import com.worldmargin.autospark.app.config.model.*
import com.worldmargin.autospark.app.config.model.SyncMode
import com.worldmargin.autospark.app.config.model.TemplateVar
import com.worldmargin.autospark.app.config.model.TemplateGroup
import com.worldmargin.autospark.app.config.model.ConversationTemplate
import com.worldmargin.autospark.app.config.model.TemplateSource

object TemplateOps {
    private const val BLANK = "blank"

    /**
     * 新建/改写模板时，从父模板继承内容（[selectedAt] 沿用原有记录，别把保留时间重置掉）。
     *
     * [variables] 是这个会话自己的变量：换继承来源、重置内容都不该把它们弄丢，调用方传进来即可。
     */
    fun inheritFrom(
        group: TemplateGroup,
        conversationId: String,
        autoSync: Boolean,
        now: Long,
        selectedAt: Long = 0L,
        variables: List<TemplateVar> = emptyList(),
    ): ConversationTemplate =
        ConversationTemplate(
            conversationId = conversationId,
            inheritFrom = group.id,
            body = group.body,
            parentHashAtSync = group.bodyHash,
            autoSync = autoSync,
            updatedAt = now,
            selectedAt = selectedAt,
            variables = variables,
        )

    /**
     * 跟随**空白模板**（不用模板、自己写；克隆来的也归这一类）。
     *
     * 和跟随自定义模板没有区别（还是「跟随父模板」、还是出现在模板页的派生列表里），
     * 唯一区别是父模板内容是空的，所以 [body] 保留用户已经写好的文案 —— 这条会照常发送。
     */
    fun blank(
        conversationId: String,
        body: String = "",
        now: Long,
        selectedAt: Long = 0L,
        variables: List<TemplateVar> = emptyList(),
    ): ConversationTemplate =
        ConversationTemplate(
            conversationId = conversationId,
            inheritFrom = SparkConfig.BUILTIN_BLANK,
            body = body,
            parentHashAtSync = Hash.of(""),
            autoSync = false,
            updatedAt = now,
            selectedAt = selectedAt,
            variables = variables,
        )

    /** 重置：回到当前继承来源的内容（空白模板就清空；这个会话自己的变量保留）。 */
    fun reset(
        template: ConversationTemplate,
        group: TemplateGroup?,
        now: Long,
    ): ConversationTemplate {
        if (group == null || template.inheritFrom == SparkConfig.BUILTIN_BLANK) {
            return blank(template.conversationId, "", now, template.selectedAt, template.variables)
        }
        return inheritFrom(group, template.conversationId, template.autoSync, now, template.selectedAt, template.variables)
    }

    /**
     * 从某个[来源][source]派生：跟随它的父模板（`source.parentId`；**为 null 的克隆也算跟随空白模板**），
     * 内容取来源当前内容并记下哈希（下次父模板同步会以它为基准）。
     */
    fun fromSource(
        conversationId: String,
        source: TemplateSource,
        autoSync: Boolean,
        now: Long,
        selectedAt: Long = 0L,
        variables: List<TemplateVar> = emptyList(),
    ): ConversationTemplate {
        val parent = source.parentId ?: SparkConfig.BUILTIN_BLANK
        return ConversationTemplate(
            conversationId = conversationId,
            inheritFrom = parent,
            body = source.body,
            parentHashAtSync = Hash.of(source.body),
            autoSync = parent != SparkConfig.BUILTIN_BLANK && autoSync,
            updatedAt = now,
            selectedAt = selectedAt,
            variables = variables,
        )
    }

    /**
     * 父模板改动后，按策略把新内容**级联**推给子树（`A → B → C` 会一层层跟下来）。
     *
     * @return 更新后的会话模板列表
     */
    fun syncChildren(
        config: SparkConfig,
        parentId: String,
        parentBody: String,
        mode: SyncMode,
        now: Long,
    ): List<ConversationTemplate> = cascade(config, parentId, parentBody, now) { child, _ -> shouldSync(child, mode) }

    /**
     * 只把新内容推给点名的这几个子模板（保存父模板时用户确认「覆盖这些」用）。
     *
     * 级联时只认点名集合，集合里的子模板会连同它的子树一起跟随。
     */
    fun syncSelected(
        config: SparkConfig,
        parentId: String,
        parentBody: String,
        selected: Set<String>,
        now: Long,
    ): List<ConversationTemplate> = cascade(config, parentId, parentBody, now) { child, _ -> child.conversationId in selected || child.inheritFrom in selected }

    /** 逐层把父内容推给子模板（[accept] 决定某个子模板这次要不要跟）。 */
    private fun cascade(
        config: SparkConfig,
        parentId: String,
        parentBody: String,
        now: Long,
        accept: (ConversationTemplate, Int) -> Boolean,
    ): List<ConversationTemplate> {
        val template = config.conversations
        val result = template.toMutableList()
        val position = template.withIndex().associate { (index, item) -> item.conversationId to index }
        val childrenOf = template.groupBy { it.inheritFrom }

        fun push(
            id: String,
            body: String,
            depth: Int,
        ) {
            if (depth > MAX_DEPTH) return
            childrenOf[id].orEmpty().forEach { child ->
                val index = position[child.conversationId] ?: return@forEach
                val current = result[index]
                if (!accept(current, depth)) return@forEach
                val next = applyParent(current, id, body, now)
                result[index] = next
                push(child.conversationId, next.body, depth + 1)
            }
        }
        push(parentId, parentBody, 0)
        return result
    }

    private fun shouldSync(
        child: ConversationTemplate,
        mode: SyncMode,
    ): Boolean =
        when (mode) {
            SyncMode.UNMODIFIED -> !child.modified
            SyncMode.MODIFIED -> child.modified
            SyncMode.ALL -> true
        }

    /** 用父模板当前内容刷新一个子模板（保留它的 autoSync 与 selectedAt）。 */
    private fun applyParent(
        child: ConversationTemplate,
        parentId: String,
        parentBody: String,
        now: Long,
    ): ConversationTemplate =
        child.copy(
            inheritFrom = parentId,
            body = parentBody,
            parentHashAtSync = Hash.of(parentBody),
            updatedAt = now,
        )

    /** 级联同步的深度上限（防配置里意外成环）。 */
    private const val MAX_DEPTH = 8
}

/** JSON 序列化（配置文件的读写格式）。 */
