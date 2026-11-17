package com.worldmargin.autospark.app.config.model

/** 宏：挂在模板 / 会话上的后置脚本。 */
data class MacroScript(
    val id: String,
    val name: String,
    val body: String,
    /** 生效目标：通用模板 id 或会话 id（两者命名空间不重叠）。 */
    val appliesTo: Set<String> = emptySet(),
    /**
     * 「仅模板」挂载：只在该模板自己的试算 / 预览里跑，
     * **不往派生会话传播**（传播的那种写在 [appliesTo] 里）。
     */
    val templateOnly: Set<String> = emptySet(),
    val updatedAt: Long = 0L,
)
