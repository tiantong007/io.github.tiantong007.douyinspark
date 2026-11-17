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
    /**
     * 完整函数声明。
     *
     * [body] 就是完整函数声明 `function 名字(形参) { … }`。
     */
    fun declaration(): String = body.trim()
}
