package com.worldmargin.autospark.host.im

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 互动表情目录：从抖音自己的 keva 配置里解析 `interactive_resource_config`。
 *
 * 为什么不在模块里硬编码：抖音给贴纸图片的 URL 是带签名的，签名有有效期
 *（我们抓包那条 `x-expires=1826161200` 到 2027-11 就废了）。抖音每次下发策略时
 * 会把**当前有效**的签名 URL 写进 `files/keva/repo/imbase_<uid>/strategy_config.sgv`，
 * 直接读它就不会过期，也能拿到「比心」「在干嘛」这些我们没抓过的贴纸。
 *
 * 只做只读解析，不缓存到磁盘；进程内缓存一次，够用。
 */
object StickerCatalog {
    /** 一条贴纸目录项。 */
    data class Item(
        val name: String,
        val staticUrl: String,
        val animateUrl: String,
        val bizType: Int,
    )

    @Volatile
    private var cache: List<Item>? = null

    /** 当前抖音下发的贴纸目录（解析失败返回空列表）。 */
    fun items(context: Context): List<Item> {
        cache?.let { return it }
        val loaded = runCatching { parse(context) }.getOrDefault(emptyList())
        cache = loaded
        // 只在真正读盘那次记一行：出问题时能一眼看出是「读不到 keva」还是「读到了但条目为空」
        com.worldmargin.autospark.core.ModLog.i(
            if (loaded.isEmpty()) "互动表情目录：没读到（退回内置）" else "互动表情目录：${loaded.size} 个",
        )
        return loaded
    }

    /** 清缓存（抖音更新资源后可在设置页重读）。 */
    fun invalidate() {
        cache = null
    }

    private fun parse(context: Context): List<Item> {
        val repos = java.io.File(context.filesDir, "keva/repo")
        val files =
            repos.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("imbase_") }
                ?.map { java.io.File(it, "strategy_config.sgv") }
                ?.filter { it.isFile }
                ?: return emptyList()
        for (file in files) {
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
            val key = "\"interactive_resource_config\"".toByteArray()
            val start = indexOf(bytes, key)
            if (start < 0) continue
            var brace = -1
            for (i in start until bytes.size) {
                if (bytes[i] == '{'.code.toByte()) {
                    brace = i
                    break
                }
            }
            if (brace < 0) continue
            // 花括号配对截出完整 JSON（后面还跟着别的键，不能直接读到文件尾）
            var depth = 0
            var end = -1
            for (i in brace until bytes.size) {
                when (bytes[i]) {
                    '{'.code.toByte() -> depth++
                    '}'.code.toByte() -> {
                        depth--
                        if (depth == 0) {
                            end = i + 1
                            break
                        }
                    }
                }
            }
            if (end <= brace) continue
            val root =
                runCatching { JSONObject(String(bytes, brace, end - brace, Charsets.UTF_8)) }
                    .getOrNull() ?: continue
            val out = mutableListOf<Item>()
            root.keys().forEach { group ->
                val node = root.optJSONObject(group) ?: return@forEach
                val list = node.optJSONArray("interactive_resources") ?: return@forEach
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    val name = item.optString("display_name")
                    val static = item.optString("static_url")
                    if (name.isBlank() || static.isBlank()) continue
                    out += Item(name, static, item.optString("animate_url"), item.optInt("biz_type", 1))
                }
            }
            if (out.isNotEmpty()) return out
        }
        return emptyList()
    }

    /** 目录转 `[{name,static,animate}]`（给脚本的 `ctx.sticker.list()` 用）。 */
    fun json(context: Context): String {
        val array = JSONArray()
        items(context).forEach { item ->
            array.put(
                JSONObject()
                    .put("name", item.name)
                    .put("static", item.staticUrl)
                    .put("animate", item.animateUrl),
            )
        }
        return array.toString()
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
