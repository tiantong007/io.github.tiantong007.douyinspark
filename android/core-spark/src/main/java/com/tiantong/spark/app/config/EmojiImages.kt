package com.tiantong.spark.app.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

/**
 * 互动表情图片的模块侧缓存：`cacheDir/emojis/<名字的 md5>.png`。
 *
 * 图片由宿主（抖音进程）按 keva 里下发的 `static_url` 下载后推过来（见
 * `host/im/EmojiImageCache`），模块自己不联网；预览页拿不到文件时退回内置图标。
 */
object EmojiImages {
    /** 名字 → 文件名（md5 十六进制 + .png），与宿主侧推送时用的 key 完全一致。 */
    fun fileName(name: String): String = md5(name.trim()) + ".png"

    /** 缓存文件（可能不存在）。 */
    fun file(context: Context, name: String): File = File(File(context.cacheDir, "emojis"), fileName(name))

    /**
     * 读成位图：优先宿主推来的缓存文件；没有就退回模块内置贴图（`assets/emojis/<md5>.png`）。
     * 两边都取不到才返回 null（调用方再退内置火焰图标）。
     */
    fun bitmap(context: Context, name: String): Bitmap? {
        val f = file(context, name)
        if (f.isFile) {
            runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()?.let { return it }
        }
        return bundled(context, name)
    }

    /** 内置贴图兜底：宿主还没推过图片时也能显示真实表情图（随包体走，安装即有）。 */
    fun bundled(context: Context, name: String): Bitmap? =
        runCatching {
            context.assets.open("emojis/" + fileName(name)).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

    /**
     * 当前可用的全部表情名：宿主同步来的 index.json（抖音 keva 下发的全量目录）
     * 并上硬编码目录（续火花，兜底——宿主还没推过时至少有它）。
     * 保序：index.json 的顺序在前，硬编码缺的补在后面。
     */
    fun names(context: Context): List<String> {
        val out = linkedSetOf<String>()
        runCatching {
            val f = File(File(context.cacheDir, "emojis"), "index.json")
            if (f.isFile) {
                val arr = org.json.JSONArray(f.readText())
                for (i in 0 until arr.length()) arr.optString(i)?.takeIf { it.isNotBlank() }?.let { out += it }
            }
        }
        com.tiantong.spark.app.config.EmojiCatalog.names().forEach { out += it }
        return out.toList()
    }

    private fun md5(text: String): String =
        MessageDigest
            .getInstance("MD5")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
