package com.tiantong.spark.app.provider

import android.content.ContentProvider
import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.io.File
import com.tiantong.spark.app.config.ConfigCodec
import com.tiantong.spark.app.config.ConfigStore

/** 跨进程配置通道的契约常量（模块进程与抖音进程共用）。 */
object ConfigContract {
    /**
     * 兜底默认值。
     *
     * 抖音进程不创建 `SparkApp`，拿不到 Application.onCreate 那次 install，
     * 所以这里必须先有一个可用的 authority：否则注入侧拼出的是 `content://` +
     * 空串，URI 非法，读配置恒失败（症状：宿主侧日志刷「模块配置不可读」）。
     */
    const val DEFAULT_AUTHORITY = "io.github.tiantong007.douyinspark.config"

    @Volatile
    var AUTHORITY: String = DEFAULT_AUTHORITY
        private set

    /** 用真实包名覆盖默认值（改包名时靠这一步纠正）。 */
    fun install(packageName: String) {
        if (packageName.isNotBlank()) AUTHORITY = "$packageName.config"
    }

    val URI: Uri get() = Uri.parse("content://$AUTHORITY")

    /** 读取全量配置，返回 Bundle["json"]。 */
    const val METHOD_READ = "read"

    /** 覆盖保存全量配置，入参 Bundle["json"]。 */
    const val METHOD_WRITE = "write"

    /** 记录某会话今日已发送，arg = conversationId，Bundle["day"]。 */
    const val METHOD_MARK_SENT = "markSent"

    /** 写入会话快照，Bundle["json"] = 快照数组 JSON。 */
    const val METHOD_PUT_SNAPSHOTS = "putSnapshots"

    /** 写入宿主侧运行简报，arg = 文本，Bundle["time"] = 时间戳。 */
    const val METHOD_NOTE = "note"

    /** 按数据保留策略清理（未在已选目标的会话，模板与发送记录超期即清），Bundle["time"] = 现在。 */
    const val METHOD_PRUNE = "prune"

    const val KEY_JSON = "json"
    const val KEY_DAY = "day"
    const val KEY_OK = "ok"
    const val KEY_TIME = "time"

    /** 管理界面 → 抖音进程：请求刷新会话快照。 */
    const val ACTION_REFRESH_SNAPSHOT = "com.tiantong.spark.action.REFRESH_SNAPSHOT"

    /** 抖音进程 → 管理界面：快照已更新。 */
    const val ACTION_SNAPSHOT_UPDATED = "com.tiantong.spark.action.SNAPSHOT_UPDATED"

    /**
     * 管理界面 → 抖音进程：立即手动发送。
     *
     * 可选带上 [EXTRA_TARGETS]（会话 id 列表）；不带则发给所有「已选目标」。
     * 手动发送不受总开关与「今日已发」限制（是用户明确点的）。
     */
    const val ACTION_SEND_NOW = "com.tiantong.spark.action.SEND_NOW"

    /** 抖音进程 → 管理界面：手动发送结束，[EXTRA_SUMMARY] 是结果摘要。 */
    const val ACTION_SEND_DONE = "com.tiantong.spark.action.SEND_DONE"

    /** 手动发送的目标会话 id 列表（ArrayList&lt;String&gt;）。 */
    const val EXTRA_TARGETS = "targets"

    /** 手动发送的结果摘要。 */
    const val EXTRA_SUMMARY = "summary"

    /**
     * 管理界面 → 抖音进程：探活。
     *
     * 宿主进程没在运行时，发出去的指令广播会石沉大海（手动续火花看起来「点了没反应」），
     * 所以界面先探活、确认在线再下指令；宿主收到立刻回 [ACTION_HOST_ALIVE]。
     */
    const val ACTION_PING_HOST = "com.tiantong.spark.action.PING_HOST"

    /** 抖音进程 → 管理界面：探活回执（同时刷新存活时间戳）。 */
    const val ACTION_HOST_ALIVE = "com.tiantong.spark.action.HOST_ALIVE"

    /** 写入宿主存活时间戳，Bundle["time"]。 */
    const val METHOD_TOUCH_HOST = "touchHost"

    /**
     * 写入一批会话头像：Bundle(key = 用户 id 的 MD5 十六进制, value = 图片字节)。
     *
     * 模块 App 不申请 `INTERNET`，头像由宿主（抖音进程）从抖音自己的缓存目录读出来后推过来，
     * 本方法只负责落盘到 `cacheDir/avatars/<key>.png`（key 规则与 AvatarLoader.fileName 一致）。
     */
    const val METHOD_PUT_AVATARS = "putAvatars"

    /** 头像图片 Bundle（key = uid 的 MD5，value = byte[]）。 */
    const val KEY_IMAGES = "images"

    /** 头像来源 URL Bundle（key 同上，value = 该头像的下载地址），用于跳过没变的图。 */
    const val KEY_URLS = "urls"

    /** 表情 key → 名字（key 与 [KEY_IMAGES] 内的 md5 key 一致），用来落盘 index.json。 */
    const val KEY_NAMES = "names"

    /** 本次实际写入的张数（int）—— 与请求张数的差就是「URL 没变、跳过」的数量。 */
    const val KEY_WRITTEN = "written"

    /**
     * 写入一批互动表情图片：Bundle(key = 名字的 MD5, value = 图片字节)。
     *
     * 和头像同样的道理：模块没有 `INTERNET`，表情图由宿主（抖音进程）按 keva 里下发的
     * `static_url` 下载后推过来，落盘到 `cacheDir/emojis/<key>.png`，
     * 供预览页显示真实表情图（没有就用内置的火焰图标兜底）。
     */
    const val METHOD_PUT_EMOJIS = "putEmojis"

    /**
     * 抖音进程 → 本模块进程：唤醒。
     *
     * 宿主调本模块的 ContentProvider 之前先发这个广播，让模块进程（连同它的 provider）先建起来。
     * 模块 App 装完从没启动过时，宿主的 provider 调用会失败（Failed to find provider info）。
     */
    const val ACTION_WAKE = "com.tiantong.spark.action.WAKE"
}

/**
 * 配置的跨进程读写入口。运行在模块 App 进程内，
 * 抖音进程通过 [ConfigContract.URI] 调用；管理界面同进程，可直接用 [ConfigStore]。
 */
class ConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        val context = context ?: return Bundle().apply { putBoolean(ConfigContract.KEY_OK, false) }
        return when (method) {
            ConfigContract.METHOD_READ -> {
                val json = ConfigCodec.encode(ConfigStore.load(context))
                Bundle().apply {
                    putBoolean(ConfigContract.KEY_OK, true)
                    putString(ConfigContract.KEY_JSON, json)
                }
            }

            ConfigContract.METHOD_WRITE -> {
                val json = extras?.getString(ConfigContract.KEY_JSON)
                if (json == null) {
                    Bundle().apply { putBoolean(ConfigContract.KEY_OK, false) }
                } else {
                    ConfigStore.save(context, ConfigCodec.decode(json))
                    Bundle().apply { putBoolean(ConfigContract.KEY_OK, true) }
                }
            }

            ConfigContract.METHOD_MARK_SENT -> {
                val day = extras?.getString(ConfigContract.KEY_DAY)
                if (arg == null || day == null) {
                    Bundle().apply { putBoolean(ConfigContract.KEY_OK, false) }
                } else {
                    ConfigStore.update(context) { config ->
                        config.copy(sentDays = config.sentDays + (arg to day))
                    }
                    Bundle().apply { putBoolean(ConfigContract.KEY_OK, true) }
                }
            }

            ConfigContract.METHOD_PUT_SNAPSHOTS -> {
                val json = extras?.getString(ConfigContract.KEY_JSON)
                if (json == null) {
                    Bundle().apply { putBoolean(ConfigContract.KEY_OK, false) }
                } else {
                    val snapshots = ConfigCodec.decode("""{"snapshots":$json}""").snapshots
                    val now = System.currentTimeMillis()
                    // 合并而不是整份替换：IM 分批吐会话，一次扫描只更新扫到的，没扫到的先留着
                    ConfigStore.update(context) { config -> config.mergeSnapshots(snapshots, now) }
                    Bundle().apply { putBoolean(ConfigContract.KEY_OK, true) }
                }
            }

            ConfigContract.METHOD_NOTE -> {
                val text = arg ?: extras?.getString(ConfigContract.KEY_JSON)
                val at = extras?.getLong(ConfigContract.KEY_TIME) ?: System.currentTimeMillis()
                if (text == null) {
                    Bundle().apply { putBoolean(ConfigContract.KEY_OK, false) }
                } else {
                    ConfigStore.update(context) { config -> config.copy(hostNote = text, hostNoteAt = at) }
                    Bundle().apply { putBoolean(ConfigContract.KEY_OK, true) }
                }
            }

            ConfigContract.METHOD_PRUNE -> {
                val now = extras?.getLong(ConfigContract.KEY_TIME) ?: System.currentTimeMillis()
                ConfigStore.update(context) { config -> config.pruneTemplates(now) }
                Bundle().apply { putBoolean(ConfigContract.KEY_OK, true) }
            }

            ConfigContract.METHOD_TOUCH_HOST -> {
                val at = extras?.getLong(ConfigContract.KEY_TIME) ?: System.currentTimeMillis()
                ConfigStore.update(context) { config -> config.copy(hostAliveAt = at) }
                Bundle().apply { putBoolean(ConfigContract.KEY_OK, true) }
            }

            ConfigContract.METHOD_PUT_AVATARS -> {
                val images = extras?.getBundle(ConfigContract.KEY_IMAGES)
                val urls = extras?.getBundle(ConfigContract.KEY_URLS) ?: Bundle()
                val written = if (images == null) -1 else writeImages(context, "avatars", images, urls)
                Bundle().apply {
                    putBoolean(ConfigContract.KEY_OK, written >= 0)
                    putInt(ConfigContract.KEY_WRITTEN, written.coerceAtLeast(0))
                }
            }

            ConfigContract.METHOD_PUT_EMOJIS -> {
                val images = extras?.getBundle(ConfigContract.KEY_IMAGES)
                val urls = extras?.getBundle(ConfigContract.KEY_URLS) ?: Bundle()
                val names = extras?.getBundle(ConfigContract.KEY_NAMES) ?: Bundle()
                val written = if (images == null) -1 else writeImages(context, "emojis", images, urls, names)
                Bundle().apply {
                    putBoolean(ConfigContract.KEY_OK, written >= 0)
                    putInt(ConfigContract.KEY_WRITTEN, written.coerceAtLeast(0))
                }
            }

            else -> Bundle().apply { putBoolean(ConfigContract.KEY_OK, false) }
        }
    }

    /**
     * 把一批图片字节落盘到 `cacheDir/<subdir>/<key>.png`，旁边记一份 `<key>.url` 记来源地址。
     *
     * key 只接受 32 位小写十六进制（AvatarLoader.fileName 产出的形状），单张上限 512 KB：
     * 宿主是被 hook 的进程，这一层不能无条件信任它的输入。
     *
     * **[urls] 里地址与 `<key>.url` 相同、且 png 已存在时跳过写入** —— 宿主每次刷新会话都会
     * 推一遍头像，不跳过就是「每秒都在重写同一批文件」，白耗 IO 还会让缓存目录频繁变更。
     *
     * @return 实际写入张数；-1 表示有非法条目（其余合法条目照常写入）
     */
    private fun writeImages(
        context: Context,
        subdir: String,
        images: Bundle,
        urls: Bundle,
        names: Bundle = Bundle(),
    ): Int {
        var ok = true
        var written = 0
        val dir = File(context.cacheDir, subdir)
        images.keySet().forEach { key ->
            val bytes = images.getByteArray(key)
            if (!HEX32.matches(key) || bytes == null || bytes.isEmpty() || bytes.size > 512 * 1024) {
                ok = false
                return@forEach
            }
            val png = File(dir, "$key.png")
            val marker = File(dir, "$key.url")
            // 去域名化：只比较/记录 URL 里 /obj/ 之后的路径，CDN 换域名时同一个对象不再被当成「变了」
            val markerValue = objectPath(urls.getString(key).orEmpty())
            if (png.isFile && markerValue.isNotEmpty() && runCatching { marker.readText() }.getOrNull() == markerValue) return@forEach
            runCatching {
                dir.mkdirs()
                png.writeBytes(bytes)
                if (markerValue.isNotEmpty()) marker.writeText(markerValue) else marker.delete()
                written++
            }.onFailure { ok = false }
        }
        if (subdir == "emojis") writeEmojiIndex(context, dir, names)
        return if (ok) written else -1
    }

    /**
     * 表情名字索引：`cacheDir/emojis/index.json` = 名字数组。
     * 宿主只按 md5 推图片，App 侧靠这个索引才能列出「抖音当前下发的全部表情」。
     * 增量合并：新名字追加到末尾，已存在的位置不动（编辑器列表顺序稳定）。
     */
    /** URL 去域名化：取 `/obj/` 起的路径；没有则原样返回。用于图片「没变就跳过写入」的比较。 */
    private fun objectPath(url: String): String {
        val i = url.indexOf("/obj/")
        return if (i >= 0) url.substring(i) else url
    }

    private fun writeEmojiIndex(
        context: Context,
        dir: File,
        names: Bundle,
    ) {
        runCatching {
            val file = File(dir, "index.json")
            val known = linkedSetOf<String>()
            if (file.isFile) {
                org.json.JSONArray(file.readText()).let { arr ->
                    for (i in 0 until arr.length()) known += arr.optString(i)
                }
            }
            names.keySet().forEach { key -> names.getString(key)?.takeIf { it.isNotBlank() }?.let { known += it } }
            val arr = org.json.JSONArray()
            known.forEach { arr.put(it) }
            dir.mkdirs()
            file.writeText(arr.toString())
        }
    }

    private companion object {
        val HEX32 = Regex("[0-9a-f]{32}")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
