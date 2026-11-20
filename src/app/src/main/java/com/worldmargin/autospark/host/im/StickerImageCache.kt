package com.worldmargin.autospark.host.im

import android.content.Context
import android.os.Bundle
import com.worldmargin.autospark.app.config.ConfigClient
import com.worldmargin.autospark.core.ModLog
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 互动表情图片：在抖音进程里下载，经 [ConfigClient.putStickers] 推给模块 App。
 *
 * 为什么要这么绕：`static_url` 指向抖音 CDN，模块自身不申请 `INTERNET`；但抖音进程本来
 * 就有网络权限，表情图又是它自己的资源，所以下载放在宿主侧做。模块侧只落盘
 * `cacheDir/stickers/<名字的 md5>.png`（见 `app/config/StickerImages`），预览页拿它显示真实图。
 *
 * 抖音并没有把这些图按可预测的名字缓存到本地（实测 `decoration_emoji` 里是别的资源），
 * 所以这里**没有**「先读本地缓存」这条路，直接按 URL 下。
 */
object StickerImageCache {
    /** 单张上限：互动表情是小图，超过这个数多半是错误页。 */
    private const val MAX_IMAGE_BYTES = 512 * 1024

    /** 单次 IPC 的图片总量上限，避免撞 binder 的 1MB transaction 限制。 */
    private const val BATCH_BYTES = 200 * 1024

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14)"

    /**
     * 把一批表情图推给模块。返回本次推送的张数。
     *
     * [items] 是 (名字, 静态图地址) 对。
     */
    fun sync(
        context: Context,
        items: Collection<Pair<String, String>>,
    ): Int {
        if (items.isEmpty()) return 0
        var pushed = 0
        var batch = Bundle()
        var batchBytes = 0
        var batchUrls = Bundle()

        fun flush() {
            if (batch.isEmpty) return
            val written = ConfigClient.putStickers(context, batch, batchUrls)
            if (written > 0) pushed += written
            batch = Bundle()
            batchUrls = Bundle()
            batchBytes = 0
        }

        items.forEach { (name, url) ->
            if (name.isBlank() || url.isBlank()) return@forEach
            val bytes = download(url) ?: return@forEach
            if (bytes.size > MAX_IMAGE_BYTES) return@forEach
            batch.putByteArray(keyOf(name), bytes)
            batchUrls.putString(keyOf(name), url)
            batchBytes += bytes.size
            if (batchBytes >= BATCH_BYTES) flush()
        }
        flush()
        return pushed
    }

    /** key 规则与模块侧 `StickerImages.fileName` 一致：名字的 UTF-8 MD5。 */
    private fun keyOf(name: String): String =
        MessageDigest
            .getInstance("MD5")
            .digest(name.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun download(url: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return runCatching {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection!!.responseCode !in 200..299) return null
            val length = connection!!.contentLengthLong
            if (length in 1..MAX_IMAGE_BYTES.toLong()) {
                val buffer = ByteArray(length.toInt())
                connection!!.inputStream.use { it.readFully(buffer) }
                buffer
            } else {
                connection!!.inputStream.use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8 * 1024)
                    while (true) {
                        val read = stream.read(chunk)
                        if (read <= 0) break
                        out.write(chunk, 0, read)
                        if (out.size() > MAX_IMAGE_BYTES) return null
                    }
                    out.toByteArray()
                }
            }
        }.onFailure { ModLog.w("表情图下载失败：$url", it) }.getOrNull()
            ?: run {
                connection?.disconnect()
                null
            }
    }

    private fun java.io.InputStream.readFully(target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = read(target, offset, target.size - offset)
            if (read <= 0) break
            offset += read
        }
    }
}
