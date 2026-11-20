package com.worldmargin.autospark.host.im

import android.content.Context
import android.os.Bundle
import com.worldmargin.autospark.app.config.ConfigClient
import com.worldmargin.autospark.core.ModLog
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 会话头像：在抖音进程里准备好图片字节，经 [ConfigClient.putAvatars] 推给模块 App。
 *
 * 模块自身**不申请 `INTERNET`**（`AvatarLoader` 只读 `cacheDir/avatars/<uid 的 md5>.png`），
 * 但抖音进程本来就有网络权限，所以取图这一步放在宿主侧做，模块一个字节都不用联网：
 *
 * 1. 先找抖音自己的头像缓存 `files/tc_effect/aweme/720x720/aweme-avatar/<url 末段>`，
 *    命中就不发请求（断网也能显示常聊会话的头像）；
 * 2. 没命中才用宿主进程的网络下载一次（8 秒超时，单张上限 512 KB）；
 * 3. 模块侧按 uid 存盘并记录来源 URL，URL 没变的不会重复覆盖写入 —— 所以
 *    「宿主反复刷新快照」不会变成「反复下载 + 反复跨进程写」。
 *
 * key 规则与模块 `AvatarLoader.fileName` 一致：uid 的 UTF-8 MD5 十六进制。
 */
object AvatarCache {
    /** 抖音头像缓存目录（相对其 files 目录）。 */
    private const val DOUYIN_AVATAR_DIR = "tc_effect/aweme/720x720/aweme-avatar"

    /** 单次 IPC 的图片总量上限，避免撞 binder 的 1MB transaction 限制。 */
    private const val BATCH_BYTES = 200 * 1024

    /** 单张上限：超出多半不是头像（错误页/大图），直接放弃。 */
    private const val MAX_IMAGE_BYTES = 512 * 1024

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14)"

    /**
     * 把头像推给模块。返回本次推送的张数。
     *
     * [items] 是 (uid, url) 对：
     * - uid 是稳定标识，用作模块侧缓存 key —— 换头像 / URL 参数变化都不影响命中；
     * - url 用来定位抖音缓存文件，也用于模块侧判断「这张图是不是已经是最新的」。
     */
    fun sync(
        context: Context,
        items: Collection<Pair<String, String>>,
    ): Int {
        if (items.isEmpty()) return 0

        // 抖音的 files 目录：宿主进程跑在抖音内，applicationInfo 就是抖音的
        val source =
            runCatching { File(context.applicationInfo.dataDir, "files/$DOUYIN_AVATAR_DIR") }
                .getOrNull()
                ?.takeIf { it.isDirectory }

        var pushed = 0
        var batch = Bundle()
        var urls = Bundle()
        var batchBytes = 0
        for ((uid, url) in items) {
            if (uid.isBlank() || url.isBlank()) continue
            val bytes = readCached(source, url) ?: download(url) ?: continue
            val key = keyOf(uid)
            batch.putByteArray(key, bytes)
            urls.putString(key, url)
            batchBytes += bytes.size
            if (batchBytes >= BATCH_BYTES) {
                pushed += ConfigClient.putAvatars(context, batch, urls).coerceAtLeast(0)
                batch = Bundle()
                urls = Bundle()
                batchBytes = 0
            }
        }
        if (batch.keySet().isNotEmpty()) {
            pushed += ConfigClient.putAvatars(context, batch, urls).coerceAtLeast(0)
        }
        if (pushed > 0) ModLog.i("头像同步 $pushed 张（按 uid 对应）")
        return pushed
    }

    /** 抖音已经缓存过的头像文件；末段可能带查询参数（`?from=...`），先去掉再匹配文件名。 */
    private fun readCached(
        dir: File?,
        url: String,
    ): ByteArray? {
        if (dir == null) return null
        val name = url.trim().substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() } ?: return null
        val file = File(dir, if (name.endsWith(".jpeg") || name.endsWith(".jpg")) name else "$name.jpeg")
        if (!file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES }
    }

    /** 在宿主进程里下载一张头像（抖音有 INTERNET 权限，模块没有）。 */
    private fun download(url: String): ByteArray? =
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.instanceFollowRedirects = true
                if (connection.responseCode !in 200..299) return null
                val bytes = connection.inputStream.use { it.readBytes() }
                bytes.takeIf { it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()

    private fun keyOf(uid: String): String =
        MessageDigest
            .getInstance("MD5")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
