package com.tiantong.spark.app.ui.v2.screen

import android.app.TimePickerDialog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.tiantong.spark.app.alarm.DailyAlarm
import com.tiantong.spark.app.ui.v2.comp.MATCH
import com.tiantong.spark.app.ui.v2.comp.WRAP
import com.tiantong.spark.app.ui.v2.comp.card
import com.tiantong.spark.app.ui.v2.comp.column
import com.tiantong.spark.app.ui.v2.comp.confirm
import com.tiantong.spark.app.ui.v2.comp.dp
import com.tiantong.spark.app.ui.v2.comp.formHint
import com.tiantong.spark.app.ui.v2.comp.lp
import com.tiantong.spark.app.ui.v2.comp.note
import com.tiantong.spark.app.ui.v2.comp.row
import com.tiantong.spark.app.ui.v2.comp.sectionLabel
import com.tiantong.spark.app.ui.v2.comp.settingRow
import com.tiantong.spark.app.ui.v2.comp.text
import com.tiantong.spark.app.ui.v2.comp.tip
import com.tiantong.spark.app.ui.v2.comp.toggleRow
import com.tiantong.spark.app.ui.v2.nav.Page
import com.tiantong.spark.app.ui.v2.nav.PageView
import com.tiantong.spark.app.ui.v2.nav.Shell
import com.tiantong.spark.app.ui.v2.theme.Tokens
import android.content.ClipData
import android.content.ClipboardManager
import com.tiantong.spark.BuildConfig
import com.tiantong.spark.app.config.ConfigCodec
import com.tiantong.spark.app.ui.v2.comp.mono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tiantong.spark.app.ui.v2.comp.divider
import com.tiantong.spark.app.ui.v2.comp.dpF

/**
 * 设置：开关 + 数据 + 诊断 + 关于，全在这一页。
 *
 * 每项都写清楚「改了会发生什么」，不放装饰性选项。
 */
class SettingsScreen(private val shell: Shell) : PageView {

    private companion object {
        const val REPO_URL = "https://github.com/tiantong007/io.github.tiantong007.douyinspark"
    }

    private val ctx = shell.context
    private var rootView: LinearLayout? = null

    override fun root(): View {
        val view = rootView ?: ctx.column().also { rootView = it }
        fill(view)
        return view
    }

    override fun refresh() {
        rootView?.let { fill(it) }
    }

    private fun fill(root: LinearLayout) {
        root.removeAllViews()
        val config = shell.state.config

        root.addView(ctx.sectionLabel("发送"))
        root.addView(
            ctx.card(8).apply {
                addView(
                    ctx.toggleRow(
                        "自动续火花",
                        "打开抖音时自动检查并发送（关掉后只能手动发）",
                        config.enabled,
                    ) { on ->
                        shell.state.update { it.copy(enabled = on) }
                    },
                )
                addView(ctx.divider())
                addView(
                    ctx.toggleRow(
                        "只在设定时间检查",
                        "关掉就是每次打开抖音都检查一遍",
                        config.checkAtAlarmOnly,
                    ) { on ->
                        shell.state.update { it.copy(checkAtAlarmOnly = on) }
                    },
                )
            },
        )

        root.addView(ctx.sectionLabel("定时唤醒"))
        root.addView(
            ctx.card(8).apply {
                addView(
                    ctx.settingRow(
                        Tokens.Icon.REFRESH,
                        "每天唤醒抖音",
                        if (config.alarmHour < 0) "未开启" else "到点自动打开抖音，锁屏也能发",
                        if (config.alarmHour < 0) "关闭" else formatTime(config.alarmHour, config.alarmMinute),
                        onClick = { pickTime() },
                    ),
                )
                if (config.alarmHour >= 0) {
                    addView(ctx.divider())
                    addView(
                        ctx.settingRow(
                            Tokens.Icon.CLOSE,
                            "关闭定时唤醒",
                            "",
                            "",
                            color = Tokens.DANGER,
                            glyphColor = Tokens.DANGER,
                            onClick = {
                                shell.state.update { it.copy(alarmHour = -1) }
                                DailyAlarm.schedule(ctx, -1, 0)
                                shell.rebuild()
                            },
                        ),
                    )
                }
            },
        )
        root.addView(
            ctx.note(
                "定时唤醒要用 root 权限拉起抖音（模块本身只在抖音进程里运行，抖音不启动就没有代码在跑）。" +
                    "没授权 root 时这一项不会生效。",
                Tokens.WARN,
                Tokens.WARN_SOFT,
            ),
        )
        root.addView(
            ctx.row().apply {
                gravity = Gravity.END
                addView(
                    ctx.text("详细说明 →", Tokens.T_SUB, Tokens.ACCENT, bold = true).apply {
                        setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(4), ctx.dp(8))
                        isClickable = true
                        setOnClickListener { shell.go(Page.HOME) }
                    },
                    ctx.lp(WRAP, WRAP),
                )
            },
            ctx.lp(MATCH, WRAP),
        )

        root.addView(ctx.sectionLabel("数据"))
        root.addView(
            ctx.card(8).apply {
                addView(
                    ctx.settingRow(
                        Tokens.Icon.DOWNLOAD, "导出配置",
                        "把模板、会话、变量复制成一段文本", "复制",
                        onClick = { exportToClipboard() },
                    ),
                )
                addView(ctx.divider())
                addView(
                    ctx.settingRow(
                        Tokens.Icon.IMPORT, "导入配置",
                        "从剪贴板里的那段文本恢复", "粘贴",
                        onClick = { importFromClipboard() },
                    ),
                )
                addView(ctx.divider())
                addView(
                    ctx.settingRow(
                        Tokens.Icon.TRASH, "清空今日已发记录",
                        "清掉后今天可以重复发（一般不用）", "",
                        color = Tokens.DANGER, glyphColor = Tokens.DANGER,
                        onClick = { clearSent() },
                    ),
                )
            },
        )

        root.addView(ctx.sectionLabel("诊断"))
        root.addView(
            ctx.card(8).apply {
                addView(
                    ctx.settingRow(
                        Tokens.Icon.BOOK, "运行日志",
                        "抖音进程最近写下的话", "查看",
                        onClick = { showHostLog() },
                    ),
                )
                addView(ctx.divider())
                addView(
                    ctx.settingRow(
                        Tokens.Icon.REFRESH, "宿主心跳",
                        "上次收到抖音进程回话的时间", heartBeat(),
                        onClick = { showHeartbeat() },
                    ),
                )
                addView(ctx.divider())
                addView(
                    ctx.settingRow(
                        Tokens.Icon.SHIELD, "安装包自校验",
                        "签名证书与资源指纹", "查看",
                        onClick = { showSelfCheck() },
                    ),
                )
            },
        )

        root.addView(ctx.sectionLabel("关于"))
        root.addView(
            ctx.card(8).apply {
                addView(
                    ctx.settingRow(Tokens.Icon.INFO, "版本", "", versionText()),
                )
                addView(ctx.divider())
                addView(
                    ctx.settingRow(
                        Tokens.Icon.BOOK, "许可与来源",
                        "Apache-2.0", "查看",
                        onClick = { showLicense() },
                    ),
                )
                addView(ctx.divider())
                addView(
                    ctx.settingRow(
                        Tokens.Icon.LINK, "源码",
                        "github.com/tiantong007", "",
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(REPO_URL),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    ),
                )
            },
        )

        root.addView(android.view.View(ctx), ctx.lp(MATCH, 28))
    }

    private fun formatTime(hour: Int, minute: Int): String =
        (if (hour < 10) "0$hour" else "$hour") + ":" + (if (minute < 10) "0$minute" else "$minute")

    private fun pickTime() {
        val config = shell.state.config
        val hour = if (config.alarmHour < 0) 8 else config.alarmHour
        val minute = if (config.alarmHour < 0) 0 else config.alarmMinute
        runCatching {
            TimePickerDialog(
                shell.activity,
                { _, h, m ->
                    shell.state.update { it.copy(alarmHour = h, alarmMinute = m, alarmAsked = true) }
                    DailyAlarm.schedule(ctx, h, m)
                    shell.rebuild()
                    ctx.tip("已设为 ${formatTime(h, m)}")
                },
                hour,
                minute,
                true,
            ).show()
        }
    }

    private fun clearSent() {
        ctx.confirm("清空今日已发记录", "清掉后，今天的会话会被认为还没发过。确定？") {
            shell.state.update { it.copy(sentDays = emptyMap()) }
            ctx.tip("已清空")
            shell.rebuild()
        }
    }

    // ── 诊断 ──────────────────────────────────────────────────────

    private fun showHostLog() {
        val text = shell.state.config.hostNote.ifBlank { "（宿主还没写过东西）" }
        shell.activity.let { activity ->
            com.tiantong.spark.app.ui.v2.comp.Sheet.open(activity, "运行日志", "宿主最近 60 行") { body ->
                body.addView(
                    ctx.mono(text, 12f, Tokens.INK_DIM).apply { setLineSpacing(ctx.dpF(3f), 1f) },
                    ctx.lp(MATCH, WRAP, top = 8),
                )
                body.addView(ctx.formHint("这些行由抖音进程写入。没有新内容说明抖音最近没启动过。"))
            }
        }
    }

    private fun showSelfCheck() {
        val info = buildString {
            append("版本：").append(versionText()).append('\n')
            append("包名：").append(ctx.packageName).append('\n')
            append("资源指纹：").append(BuildConfig.CONTENT_SHA256.take(16)).append("…\n")
            append("ABI：").append(android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()).append('\n')
            append('\n')
            append("打开界面时会在后台核一遍签名证书与资源指纹，结果写进模块日志：")
            append("「自校验：签名 OK / 资源指纹 OK」。")
        }
        shell.activity.let { activity ->
            com.tiantong.spark.app.ui.v2.comp.Sheet.open(activity, "安装包自校验") { body ->
                body.addView(
                    ctx.mono(info, 12f, Tokens.INK).apply { setLineSpacing(ctx.dpF(4f), 1f) },
                    ctx.lp(MATCH, WRAP, top = 8),
                )
            }
        }
    }

    private fun heartBeat(): String {
        val at = shell.state.config.hostAliveAt
        if (at <= 0) return "从未"
        val minutes = (System.currentTimeMillis() - at) / 60000
        return if (minutes < 1) "刚刚" else "$minutes 分钟前"
    }

    private fun showHeartbeat() {
        val config = shell.state.config
        val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        val text = buildString {
            append("上次回话：").append(if (config.hostAliveAt > 0) format.format(Date(config.hostAliveAt)) else "从未").append('\n')
            append("上次写日志：").append(if (config.hostNoteAt > 0) format.format(Date(config.hostNoteAt)) else "从未").append('\n')
            append('\n')
            append("宿主 = 抖音进程里的模块代码，只在抖音运行时活着；")
            append("「从未」通常表示装完还没打开过抖音。")
        }
        shell.activity.let { activity ->
            com.tiantong.spark.app.ui.v2.comp.Sheet.open(activity, "宿主心跳") { body ->
                body.addView(ctx.mono(text, 12f, Tokens.INK).apply { setLineSpacing(ctx.dpF(4f), 1f) }, ctx.lp(MATCH, WRAP, top = 8))
            }
        }
    }

    // ── 数据 ──────────────────────────────────────────────────────

    private fun clipboard(): ClipboardManager? =
        ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private fun exportToClipboard() {
        val json = runCatching { ConfigCodec.encode(shell.state.config) }.getOrNull()
        if (json.isNullOrBlank()) {
            ctx.tip("导出失败")
            return
        }
        clipboard()?.setPrimaryClip(ClipData.newPlainText("抖音续火花配置", json))
        ctx.tip("已复制 ${json.length} 个字符到剪贴板")
    }

    private fun importFromClipboard() {
        val text = clipboard()?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) {
            ctx.tip("剪贴板是空的")
            return
        }
        val incoming = ConfigCodec.decodeOrNull(text) ?: run {
            ctx.tip("这段文本不是本模块的配置")
            return
        }
        ctx.confirm(
            "导入配置",
            "将覆盖当前的 ${shell.state.config.groups.size} 个模板 / ${shell.state.config.conversations.size} 个会话设置。" +
                "导入的是 ${incoming.groups.size} 个模板 / ${incoming.conversations.size} 个会话。",
            positive = "覆盖导入",
            danger = true,
        ) {
            shell.state.save(incoming)
            ctx.tip("已导入")
            shell.rebuild()
        }
    }

    // ── 关于 ──────────────────────────────────────────────────────

    private fun showLicense() {
        shell.activity.let { activity ->
            com.tiantong.spark.app.ui.v2.comp.Sheet.open(activity, "许可与来源", "Apache-2.0") { body ->
                body.addView(
                    ctx.text(
                        "本模块以 Apache-2.0 发布，包含第三方代码（已修改）。许可全文见仓库根目录的 LICENSE。",
                        13f,
                        Tokens.INK,
                    ).apply { setLineSpacing(ctx.dpF(4f), 1f) },
                    ctx.lp(MATCH, WRAP, top = 8),
                )
            }
        }
    }

    private fun versionText(): String =
        runCatching {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "${info.versionName}（${info.versionCode}）"
        }.getOrDefault("未知")
}
