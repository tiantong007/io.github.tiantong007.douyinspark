package com.worldmargin.autospark.app.config

/**
 * 互动表情（msgType=5）内容目录。
 *
 * 内容来自抖音真实发送链路抓包：Message$Builder.build() 出参的 content JSON。
 * 脚本里写 `ctx.result.push("[STICKER:续火花]")` 即发送对应互动表情。
 */
object StickerCatalog {
    /** 标记前缀：[STICKER: */
    const val MARK_PREFIX = "[STICKER:"

    /** 标记后缀 ] */
    const val MARK_SUFFIX = "]"

    /** 是否为互动表情标记 */
    fun isStickerMark(text: String): Boolean {
        val t = text.trim()
        return t.startsWith(MARK_PREFIX) && t.endsWith(MARK_SUFFIX) && t.length > MARK_PREFIX.length + MARK_SUFFIX.length
    }

    /** 从标记里取出表情名 */
    fun nameOf(text: String): String {
        val t = text.trim()
        return t.substring(MARK_PREFIX.length, t.length - MARK_SUFFIX.length).trim()
    }

    /**
     * 表情名 → content JSON。
     *
     * url 里的签名有效期到 2027-11，可直接复用；若哪天失效，用 frida hook
     * `com.bytedance.im.core.model.Message$Builder.build()` 重新抓一条即可。
     */
    private val CONTENT: Map<String, String> =
        mapOf(
            "续火花" to """{"danmaku_id":"","danmaku_text":"","display_name":"续火花","emoji_from":"lite","id":"0","emoji_source":"","emoji_type":"lite_emoji","height":100,"image_id":0,"image_type":"png","match_uid":"","message_reply_display_type":0,"package_id":0,"show_notice":false,"source_schema":"","resource_type":4,"sticker_type":12,"top_desc_scene_type":0,"top_desc_uid_match":"","updateConversationTime":true,"url":{"height":100,"data_size":0,"uri":"https://p11-sign.douyinpic.com/obj/im-resource/1687263281313-ts-e7bbade781abe88ab12e706e67?lk3s\u003d91c5b7cb\u0026x-expires\u003d1826161200\u0026x-signature\u003dMeMoJJnToGOJ%2BdDUy05x7wcKqB8%3D\u0026from\u003d2445653963\u0026s\u003dim_111\u0026se\u003dfalse\u0026sc\u003dimage\u0026biz_tag\u003daweme_im\u0026l\u003d202611141130529D44EC529AF2A0840C9D","url_list":["https://p11-sign.douyinpic.com/obj/im-resource/1687263281313-ts-e7bbade781abe88ab12e706e67?lk3s\u003d91c5b7cb\u0026x-expires\u003d1826161200\u0026x-signature\u003dMeMoJJnToGOJ%2BdDUy05x7wcKqB8%3D\u0026from\u003d2445653963\u0026s\u003dim_111\u0026se\u003dfalse\u0026sc\u003dimage\u0026biz_tag\u003daweme_im\u0026l\u003d202611141130529D44EC529AF2A0840C9D"],"width":100},"version":"2","width":100,"createdAt":0,"is_card":false,"msgHint":"","aweType":507}""",
        )

    fun byName(name: String): String? = CONTENT[name.trim()]

    fun names(): List<String> = CONTENT.keys.toList()

    /**
     * 用抖音当前下发的图片地址，套我们抓包拿到的**全量字段**拼 content。
     *
     * keva 里只有 `display_name` / `static_url` / `animate_url` / `biz_type` 四项，
     * 其余字段（`emoji_type`、`sticker_type`、`aweType` 等）是抖音 IM 侧解析时真正要的，
     * 上游那套精简 content 在个别机型上会被拒，所以这里以抓包模板为底，只替换名字与地址。
     *
     * @param name 表情名（`display_name`）
     * @param url 抖音当前下发的静态图地址（带有效签名）
     * @return 拼好的 content JSON；模板解析失败返回 null（调用方退回硬编码）
     */
    fun contentWithUrl(name: String, url: String): String? {
        if (name.isBlank() || url.isBlank()) return null
        val template = CONTENT.values.firstOrNull() ?: return null
        val root = runCatching { org.json.JSONObject(template) }.getOrNull() ?: return null
        val link = root.optJSONObject("url") ?: return null
        link.put("uri", url)
        link.put("url_list", org.json.JSONArray().put(url))
        root.put("display_name", name.trim())
        root.put("id", name.trim())
        return root.toString()
    }
}
