package com.worldmargin.autospark.app.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

/**
 * 互动表情图片的模块侧缓存：`cacheDir/stickers/<名字的 md5>.png`。
 *
 * 图片由宿主（抖音进程）按 keva 里下发的 `static_url` 下载后推过来（见
 * `host/im/StickerImageCache`），模块自己不联网；预览页拿不到文件时退回内置图标。
 */
object StickerImages {
    /** 名字 → 文件名（md5 十六进制 + .png），与宿主侧推送时用的 key 完全一致。 */
    fun fileName(name: String): String = md5(name.trim()) + ".png"

    /** 缓存文件（可能不存在）。 */
    fun file(context: Context, name: String): File = File(File(context.cacheDir, "stickers"), fileName(name))

    /** 读成位图；文件不存在或解码失败返回 null。 */
    fun bitmap(context: Context, name: String): Bitmap? {
        val f = file(context, name)
        if (!f.isFile) return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }

    private fun md5(text: String): String =
        MessageDigest
            .getInstance("MD5")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
