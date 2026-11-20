package com.worldmargin.autospark.app.config

import android.content.Context
import android.os.Bundle
import com.worldmargin.autospark.app.provider.ConfigContract
import com.worldmargin.autospark.app.config.ConfigCodec
import com.worldmargin.autospark.app.config.model.ConversationSnapshot
import com.worldmargin.autospark.app.config.SparkConfig
import com.worldmargin.autospark.app.provider.ConfigProvider

/**
 * 抖音进程访问配置的客户端（走 ContentResolver → [ConfigProvider]）。
 *
 * 所有方法都会在跨进程失败时安全返回，不抛出——宿主侧调用点只需处理 null/false。
 */
object ConfigClient {
    fun read(context: Context): SparkConfig? =
        runCatching {
            val result = context.contentResolver.call(ConfigContract.URI, ConfigContract.METHOD_READ, null, null)
            val json = result?.getString(ConfigContract.KEY_JSON) ?: return null
            ConfigCodec.decode(json)
        }.getOrNull()

    fun write(
        context: Context,
        config: SparkConfig,
    ): Boolean =
        runCatching {
            val extras = Bundle().apply { putString(ConfigContract.KEY_JSON, ConfigCodec.encode(config)) }
            context.contentResolver.call(ConfigContract.URI, ConfigContract.METHOD_WRITE, null, extras)
                ?.getBoolean(ConfigContract.KEY_OK) == true
        }.getOrDefault(false)

    fun markSent(
        context: Context,
        conversationId: String,
        day: String,
    ): Boolean =
        runCatching {
            val extras = Bundle().apply { putString(ConfigContract.KEY_DAY, day) }
            context.contentResolver.call(ConfigContract.URI, ConfigContract.METHOD_MARK_SENT, conversationId, extras)
                ?.getBoolean(ConfigContract.KEY_OK) == true
        }.getOrDefault(false)

    /** 写入宿主侧运行简报（管理界面的「设置 → 宿主状态」直接读配置里的这份文本）。 */
    fun note(
        context: Context,
        text: String,
        at: Long,
    ): Boolean =
        runCatching {
            val extras = Bundle().apply { putLong(ConfigContract.KEY_TIME, at) }
            context.contentResolver.call(ConfigContract.URI, ConfigContract.METHOD_NOTE, text, extras)
                ?.getBoolean(ConfigContract.KEY_OK) == true
        }.getOrDefault(false)

    /** 触发一次数据保留清理（宿主写完全快照后调用）。 */
    fun prune(
        context: Context,
        now: Long,
    ): Boolean =
        runCatching {
            val extras = Bundle().apply { putLong(ConfigContract.KEY_TIME, now) }
            context.contentResolver.call(ConfigContract.URI, ConfigContract.METHOD_PRUNE, null, extras)
                ?.getBoolean(ConfigContract.KEY_OK) == true
        }.getOrDefault(false)

    /** 刷新宿主存活时间戳（宿主进程每次响应指令时调用）。 */
    fun touchHost(
        context: Context,
        at: Long,
    ): Boolean =
        runCatching {
            val extras = Bundle().apply { putLong(ConfigContract.KEY_TIME, at) }
            context.contentResolver.call(ConfigContract.URI, ConfigContract.METHOD_TOUCH_HOST, null, extras)
                ?.getBoolean(ConfigContract.KEY_OK) == true
        }.getOrDefault(false)

    /**
     * 推送一批会话头像给模块进程落盘（Bundle key = uid 的 MD5，value = 图片字节）。
     *
     * 模块不申请 `INTERNET`，头像由宿主准备字节后推过去。[urls] 用于让模块侧跳过
     * 「地址没变」的重复写入。
     *
     * @return 实际写入张数；通道不可用时返回 -1
     */
    fun putAvatars(
        context: Context,
        images: Bundle,
        urls: Bundle,
    ): Int =
        runCatching {
            val extras =
                Bundle().apply {
                    putBundle(ConfigContract.KEY_IMAGES, images)
                    putBundle(ConfigContract.KEY_URLS, urls)
                }
            val result = context.contentResolver.call(ConfigContract.URI, ConfigContract.METHOD_PUT_AVATARS, null, extras)
            if (result?.getBoolean(ConfigContract.KEY_OK) == true) {
                result.getInt(ConfigContract.KEY_WRITTEN)
            } else {
                -1
            }
        }.getOrDefault(-1)

    fun putSnapshots(
        context: Context,
        snapshots: List<ConversationSnapshot>,
    ): Boolean =
        runCatching {
            val extras =
                Bundle().apply {
                    putString(ConfigContract.KEY_JSON, ConfigCodec.encodeSnapshots(snapshots).toString())
                }
            context.contentResolver.call(ConfigContract.URI, ConfigContract.METHOD_PUT_SNAPSHOTS, null, extras)
                ?.getBoolean(ConfigContract.KEY_OK) == true
        }.getOrDefault(false)
}
