package com.worldmargin.autospark.app.config

/** 内置文案模板（模板页里可直接「添加为我的模板」或一键复制给会话）。 */
data class BuiltinTemplate(
    val id: String,
    val name: String,
    val desc: String,
    val body: String,
)

/**
 * 内置模板：只是**可选**的起点，不会自动套给任何会话。
 *
 * 显示顺序 = [ALL] 的顺序。cc 版精简为 3 个：
 * 空白模板 / 续火花表情 / 通用问候。
 */
object BuiltinTemplates {
    /** 空白：不用模板，会话自己写文案（照常发送）。 */
    val BLANK =
        BuiltinTemplate(
            id = "blank",
            name = "空白模板",
            desc = "不用模板，自己写",
            body = "",
        )

    /** 互动表情「续火花」（火焰贴纸）。只有支持 [STICKER:表情名] 标记的版本才会走表情通道。 */
    val SPARK_STICKER =
        BuiltinTemplate(
            id = "spark_sticker",
            name = "续火花表情",
            desc = "固定内容：互动表情「续火花」（火焰贴纸）",
            body = """ctx.result.push("[STICKER:续火花]");""",
        )

    /**
     * 通用问候：合并了原来的「按时问候 / 火花天数打卡 / 简短提醒」。
     *
     * 按当前时段选招呼语（凌晨不说早安），再从四个句子里随机抽一条，
     * 带上称呼、火花天数、当天日期。
     */
    val GREETING =
        BuiltinTemplate(
            id = "greeting",
            name = "通用问候",
            desc = "按时段问候 + 火花天数 + 简短提醒，随机组合",
            body =
                """var who = ctx.io.name || "朋友";
var hour = parseInt(ctx.builtin.time.split(":")[0], 10);
var hi = hour < 5 ? "夜深了" : hour < 11 ? "早安" : hour < 14 ? "午安" : hour < 18 ? "下午好" : hour < 23 ? "晚上好" : "夜深了";
ctx.result.push(ctx.builtin.pick([
  hi + "～" + who + "，今天也要记得续火花哦",
  "火花打卡 " + ctx.builtin.date + "：" + who + "，已经 " + ctx.io.days + " 天啦",
  "滴——" + who + "，第 " + ctx.io.days + " 天的火花，别让它灭了",
  who + "，今天也来续一下火花吧"
]));""",
        )

    val ALL = listOf(BLANK, SPARK_STICKER, GREETING)

    fun byId(id: String): BuiltinTemplate? = ALL.firstOrNull { it.id == id }
}
