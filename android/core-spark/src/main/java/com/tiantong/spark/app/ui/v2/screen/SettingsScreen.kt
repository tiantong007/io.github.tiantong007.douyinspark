package com.tiantong.spark.app.ui.v2.screen

import android.app.TimePickerDialog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.tiantong.spark.app.ui.v2.comp.chips
import com.tiantong.spark.app.ui.v2.comp.shape
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
import com.tiantong.spark.BuildConfig
import com.tiantong.spark.app.ui.v2.comp.mono
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
                "定时唤醒优先免 root 拉起抖音（向抖音 preload provider 取 client 触发主进程启动，不需要 su）；" +
                    "该方式不可达时自动回退 root + monkey。模块本身只在抖音进程里运行，抖音不启动就没有代码在跑。",
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

    // ── 诊断 ──────────────────────────────────────────────────────

    private data class LogEntry(
        val kind: String,
        val time: String = "",
        val name: String = "",
        val state: Int = 0,
        val days: Int = 0,
        val text: String = "",
        val recover: String = "",
        val head: String = "",
        val sent: Int = 0,
        val skip: Int = 0,
        val fail: Int = 0,
        val tag: String = "",
        val who: String = "",
        val body: String = "",
    )

    private fun showHostLog() {
        val activity = shell.activity
        val entries = parseHostNote(shell.state.config.hostNote)
        var bodyRef: LinearLayout? = null
        var renderRef: ((Int) -> Unit)? = null

        val render: (Int) -> Unit = { f ->
            bodyRef?.let { body ->
                body.removeAllViews()
                val st = entries.filter { it.kind == "state" }
                val lit = st.count { it.state == 1 }
                val wait = st.count { it.state == 2 || it.state == 3 }
                val none = st.count { it.state == -1 }
                val sentN = entries.filter { it.kind == "result" }.sumOf { it.sent }
                val bad = entries.count { it.kind == "bad" }
                val head = if (entries.isEmpty()) {
                    "宿主还没回写过东西。进一次抖音，再回来点刷新。"
                } else {
                    "今天已发 $sentN 条 · 抓到 ${st.size} 个会话（在燃 $lit / 待回 $wait / 无火花 $none）" +
                        if (bad > 0) " · $bad 条异常" else ""
                }
                body.addView(ctx.text(head, Tokens.T_SUB, Tokens.INK_DIM).apply { setLineSpacing(ctx.dpF(2f), 1f) }, ctx.lp(MATCH, WRAP, bottom = 2))
                body.addView(ctx.chips(listOf("全部", "发送", "状态", "异常"), f) { i -> renderRef?.invoke(i) }, ctx.lp(MATCH, WRAP))
                val rows = when (f) {
                    1 -> entries.filter { it.kind == "result" || it.kind == "detail" }
                    2 -> st
                    3 -> entries.filter { it.kind == "bad" }
                    else -> entries
                }
                if (rows.isEmpty()) {
                    body.addView(
                        ctx.text(if (entries.isEmpty()) "没有内容。" else "这一类暂时没有记录。", Tokens.T_SUB, Tokens.INK_FAINT).apply { setPadding(0, ctx.dp(18), 0, 0) },
                        ctx.lp(MATCH, WRAP),
                    )
                } else {
                    rows.asReversed().forEach { e -> body.addView(logRowView(e), ctx.lp(MATCH, WRAP)) }
                }
            }
        }
        renderRef = render

        com.tiantong.spark.app.ui.v2.comp.Sheet.open(activity, "运行日志", "宿主最近回写") { body ->
            bodyRef = body
            render(0)
        }
    }

    private fun logRowView(e: LogEntry): View {
        val time = ctx.text(e.time, Tokens.T_TINY, Tokens.INK_FAINT)
        return when (e.kind) {
            "state" -> ctx.row().apply {
                setPadding(0, ctx.dp(7), 0, ctx.dp(7))
                addView(time, ctx.lp(56, WRAP))
                addView(
                    ctx.column().apply {
                        val line = ctx.row()
                        line.addView(ctx.text(e.name, Tokens.T_SUB, Tokens.INK, bold = true), ctx.lp(WRAP, WRAP))
                        val style = flameStyle(e.state)
                        line.addView(pill(style.first, style.second, style.third), ctx.lp(WRAP, WRAP).also { it.leftMargin = ctx.dp(6) })
                        addView(line, ctx.lp(MATCH, WRAP))
                        val detail = stateDetail(e)
                        if (detail.isNotEmpty()) addView(ctx.text(detail, Tokens.T_TINY, Tokens.INK_DIM), ctx.lp(MATCH, WRAP, top = 2))
                    },
                    ctx.lp(0, WRAP, 1f),
                )
            }
            "result" -> ctx.row().apply {
                setPadding(0, ctx.dp(7), 0, ctx.dp(7))
                addView(time, ctx.lp(56, WRAP))
                addView(
                    ctx.column().apply {
                        val line = ctx.row()
                        line.addView(ctx.text(e.head, Tokens.T_SUB, Tokens.INK, bold = true), ctx.lp(WRAP, WRAP))
                        line.addView(pill("已发 ${e.sent}", if (e.sent > 0) Tokens.OK else Tokens.INK_DIM, if (e.sent > 0) Tokens.OK_SOFT else Tokens.SEG_BG), ctx.lp(WRAP, WRAP).also { it.leftMargin = ctx.dp(6) })
                        line.addView(pill("跳过 ${e.skip}", Tokens.INK_DIM, Tokens.SEG_BG), ctx.lp(WRAP, WRAP).also { it.leftMargin = ctx.dp(4) })
                        line.addView(pill("失败 ${e.fail}", if (e.fail > 0) Tokens.DANGER else Tokens.INK_DIM, if (e.fail > 0) Tokens.DANGER_SOFT else Tokens.SEG_BG), ctx.lp(WRAP, WRAP).also { it.leftMargin = ctx.dp(4) })
                        addView(line, ctx.lp(MATCH, WRAP))
                    },
                    ctx.lp(0, WRAP, 1f),
                )
            }
            "detail" -> ctx.row().apply {
                setPadding(0, ctx.dp(5), 0, ctx.dp(5))
                addView(time, ctx.lp(56, WRAP))
                val fg = when (e.tag) { "已发" -> Tokens.OK; "失败" -> Tokens.DANGER; else -> Tokens.WARN }
                val bg = when (e.tag) { "已发" -> Tokens.OK_SOFT; "失败" -> Tokens.DANGER_SOFT; else -> Tokens.WARN_SOFT }
                addView(pill(e.tag, fg, bg), ctx.lp(WRAP, WRAP).also { it.rightMargin = ctx.dp(8) })
                addView(ctx.text(e.who, Tokens.T_SUB, Tokens.INK), ctx.lp(0, WRAP, 1f))
            }
            else -> ctx.row().apply {
                setPadding(0, ctx.dp(5), 0, ctx.dp(5))
                addView(time, ctx.lp(56, WRAP))
                val color = when (e.kind) { "bad" -> Tokens.DANGER; "warn" -> Tokens.WARN; "ok" -> Tokens.OK; else -> Tokens.INK_DIM }
                addView(ctx.text(e.body, Tokens.T_SUB, color).apply { setLineSpacing(ctx.dpF(2f), 1f) }, ctx.lp(0, WRAP, 1f))
            }
        }
    }

    private fun pill(label: String, fg: Int, bg: Int): TextView =
        ctx.text(label, 10f, fg, bold = true).apply {
            setPadding(ctx.dp(6), ctx.dp(2), ctx.dp(6), ctx.dp(2))
            background = ctx.shape(bg, Tokens.R_PILL)
        }

    private fun flameStyle(state: Int): Triple<String, Int, Int> = when (state) {
        1 -> Triple("在燃", Tokens.OK, Tokens.OK_SOFT)
        2 -> Triple("待对方回", Tokens.WARN, Tokens.WARN_SOFT)
        3 -> Triple("可重燃", Tokens.INFO, Tokens.INFO_SOFT)
        else -> Triple("无火花", Tokens.MUTED, Tokens.MUTED_SOFT)
    }

    private fun stateDetail(e: LogEntry): String {
        val parts = ArrayList<String>()
        if (e.days > 0) parts.add("火花 ${e.days} 天")
        if (e.recover.isNotEmpty()) parts.add(e.recover)
        if (e.state == 3 && e.text.isNotEmpty() && !e.text.matches(Regex("^\\d+$"))) parts.add(e.text)
        if (e.state == -1 && e.text == "可点亮") parts.add("可点亮")
        return parts.joinToString(" · ")
    }

    private fun parseHostNote(raw: String): List<LogEntry> {
        val out = ArrayList<LogEntry>()
        if (raw.isBlank()) return out
        val lineRe = Regex("^(\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d)\\s+([\\s\\S]*)$")
        val stateRe = Regex("^(.+?) · hasSpark=(true|false) state=(-?\\d+) days=(\\d+) text=(.*?)(?: level=|$)")
        val rawRe = Regex("^raw\\[(.*?)\\]=([\\s\\S]*)$")
        val resultRe = Regex("^(手动发送|续火花结果)：已发 (\\d+) / 跳过 (\\d+) / 失败 (\\d+)")
        val detailRe = Regex("^(已发|跳过|失败)：(.*)$")
        var last = -1
        raw.split("\n").forEach { rawLine ->
            val l = rawLine.trim()
            if (l.isEmpty()) return@forEach
            val m = lineRe.find(l)
            val time = m?.groupValues?.get(1) ?: ""
            val body = m?.groupValues?.get(2) ?: l
            val st = stateRe.find(body)
            if (st != null) {
                out.add(
                    LogEntry(
                        kind = "state",
                        time = time,
                        name = st.groupValues[1],
                        state = st.groupValues[3].toIntOrNull() ?: 0,
                        days = st.groupValues[4].toIntOrNull() ?: 0,
                        text = st.groupValues[5].trim(),
                    ),
                )
                last = out.size - 1
                return@forEach
            }
            val rw = rawRe.find(body)
            if (rw != null) {
                if (last >= 0 && out[last].name == rw.groupValues[1]) {
                    val par = runCatching { org.json.JSONObject(rw.groupValues[2]) }.getOrNull()
                    val arr = par?.optJSONArray("flame_infos")
                    if (arr != null) {
                        for (k in 0 until arr.length()) {
                            val f = arr.optJSONObject(k) ?: continue
                            if (f.optInt("state") == 3 && f.optInt("recover_need_days", 0) > 0) {
                                out[last] = out[last].copy(recover = "重燃 ${f.optInt("recover_days")}/${f.optInt("recover_need_days")}")
                                break
                            }
                        }
                    }
                }
                return@forEach
            }
            if (body.contains("无火花数据")) {
                out.add(LogEntry(kind = "state", time = time, name = body.replace(Regex("\\s*·\\s*无火花数据\\s*"), ""), state = -1))
                last = out.size - 1
                return@forEach
            }
            val rs = resultRe.find(body)
            if (rs != null) {
                out.add(LogEntry(kind = "result", time = time, head = rs.groupValues[1], sent = rs.groupValues[2].toIntOrNull() ?: 0, skip = rs.groupValues[3].toIntOrNull() ?: 0, fail = rs.groupValues[4].toIntOrNull() ?: 0))
                return@forEach
            }
            val dt = detailRe.find(body)
            if (dt != null) {
                out.add(LogEntry(kind = "detail", time = time, tag = dt.groupValues[1], who = dt.groupValues[2]))
                return@forEach
            }
            val kind = when {
                body.startsWith("待发") || body.startsWith("待续") -> "warn"
                Regex("失败|错误|未就绪|不可读|异常|Exception|error", RegexOption.IGNORE_CASE).containsMatchIn(body) -> "bad"
                Regex("已发|成功|已同步|已刷新").containsMatchIn(body) -> "ok"
                Regex("跳过|等待|未开始").containsMatchIn(body) -> "warn"
                else -> "plain"
            }
            out.add(LogEntry(kind = kind, time = time, body = body))
        }
        return out
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
