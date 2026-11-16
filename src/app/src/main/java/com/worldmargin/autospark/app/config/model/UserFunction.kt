package com.worldmargin.autospark.app.config.model

/** 自定义函数：注入成 `ctx.functions.<名字>`。 */
data class UserFunction(
    val id: String,
    val name: String,
    val params: String,
    val body: String,
    val desc: String = "",
    val updatedAt: Long = 0L,
) {
    /** 形参名列表（去掉空项）。 */
    val paramList: List<String>
        get() = params.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * 完整函数声明。
     *
     * [body] 就是完整函数声明 `function 名字(形参) { … }`。
     */
    fun declaration(): String = body.trim()
}

/**
 * 继承来源：内置模板、自定义模板，或某个会话的派生模板。
 *
 * [parentId] 是「跟随谁」：内置模板用 `builtin:xxx`、自定义模板用模板 id；
 * 为 null 只会出现在未升级的旧数据里（读入时会归到空白模板）。
 */
