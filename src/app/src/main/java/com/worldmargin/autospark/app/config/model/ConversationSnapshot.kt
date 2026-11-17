package com.worldmargin.autospark.app.config.model

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
