package com.worldmargin.autospark.app.config.model

import org.json.JSONArray
import org.json.JSONObject
import com.worldmargin.autospark.app.config.SparkConfig
/** 会话快照：从抖音扫回来的会话 + 火花状态。 */
data class ConversationSnapshot(
    val conversationId: String,
    val name: String,
    val remark: String,
    val avatar: String,
    val peerUid: String,
    val days: Int,
    val state: Int,
    val stateText: String,
    val recoverDays: Int,
    val recoverNeedDays: Int,
    /** 宿主原生展示文案（如「重燃中 2/3」）；recoverDays/recoverNeedDays 是时间轴预测值，界面用这个。 */
    val flameText: String = "",
    /** 未来提示（如「6 天后消失」），没有就是空串。 */
    val expireText: String = "",
    val hasSpark: Boolean,
    val needsRenewal: Boolean,
    val isGroup: Boolean,
    val updatedAt: Long,
    /** 连续多少次扫描没再出现（出现一次就清零），见 [SparkConfig.HIDDEN_AFTER_MISSES]。 */
    val missCount: Int = 0,
) {
    val displayName: String get() = remark.ifBlank { name }

    /** 连续 [SparkConfig.HIDDEN_AFTER_MISSES] 次没出现：从会话列表里隐藏，但数据还留着。 */
    val isHidden: Boolean get() = missCount >= SparkConfig.HIDDEN_AFTER_MISSES
}

/** 全量配置：模板 + 发送记录 + 会话快照 + 手动归属。 */
/**
 * 沙盒变量：脚本通过 `ctx.sandbox.setvar` 写下的数据，**跳次运行保留**，值存成 JSON 文本。
 *
 * 归属（谁创建谁管理）：
 * - [owner] 为 `template:<会话模板 id>`：模板创建的，宏可以读、也可以改；
 * - [owner] 为 `macro:<宏 id>`：宏自己创建的，模板读不到也写不了，只有创建它的宏能删。
 */
