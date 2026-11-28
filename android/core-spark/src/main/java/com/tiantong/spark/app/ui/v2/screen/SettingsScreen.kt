package com.tiantong.spark.app.ui.v2.screen

import android.app.TimePickerDialog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.tiantong.spark.app.ui.v2.comp.chips
import com.tiantong.spark.app.ui.v2.comp.gradient
import com.tiantong.spark.app.ui.v2.comp.shape
import com.tiantong.spark.app.alarm.DailyAlarm
import com.tiantong.spark.app.alarm.WakeLog
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
 * 设置：顶部概览卡 + 四组（续火花 / 定时唤醒 / 数据与工具 / 关于），与网页原型同版式。
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

        root.addView(settingsHero())

        // 续火花
        root.addView(secHead("续火花", "自动续火花、任务收尾、补发、定时唤醒都集中在这里；关掉「自动续火花」只能手动发。"))
        root.addView(
            ctx.card(8).apply {
                addView(
                    ctx.toggleRow("自动续火花", "打开抖音时自动检查并发送", config.enabled) { on ->
                        shell.state.update { it.copy(enabled = on) }
                    },
                )
                addView(groupGap())
                addView(
                    ctx.toggleRow("定时唤醒", "开启后到点自动打开抖音，锁屏也能发", config.alarmHour >= 0) { on ->
                        if (on) {
                            val h = if (config.alarmHour < 0) 8 else config.alarmHour
                            val m = if (config.alarmHour < 0) 0 else config.alarmMinute
                            shell.state.update { it.copy(alarmHour = h, alarmMinute = m, alarmAsked = true) }
                            DailyAlarm.schedule(ctx, h, m)
                        } else {
                            shell.state.update { it.copy(alarmHour = -1) }
                            DailyAlarm.schedule(ctx, -1, 0)
                        }
                        shell.rebuild()
                        if (on) showAlarmPerm()
                    },
                )
                if (config.alarmHour >= 0) {
                    addView(ctx.divider())
                    addView(
                        ctx.toggleRow("只在设定时间检查", "关掉就是每次打开抖音都检查一遍", config.checkAtAlarmOnly) { on ->
                            shell.state.update { it.copy(checkAtAlarmOnly = on) }
                        },
                    )
                    addView(ctx.divider())
                    addView(
                        ctx.settingRow(
                            Tokens.Icon.REFRESH, "唤醒时间", "每天这个时间自动叫醒抖音",
                            formatTime(config.alarmHour, config.alarmMinute), onClick = { pickTime() },
                        ),
                    )
                    addView(ctx.divider())
                    addView(
                        ctx.settingRow(
                            Tokens.Icon.SHIELD, "权限检查", alarmPermSummary(), "去开启",
                            onClick = { showAlarmPerm() },
                        ),
                    )
                    addView(ctx.divider())
                    addView(
                        ctx.settingRow(
                            Tokens.Icon.SHIELD, "调度兜底", "定时唤醒被系统清掉时，每 15 分钟自动补排一次", "正常",
                            glyphColor = 0xFF35D0A5.toInt(),
                        ),
                    )
                    addView(ctx.divider())
                    addView(
                        ctx.settingRow(
                            Tokens.Icon.BOOK, "唤醒日志", wakeSummary(), "查看", onClick = { showWakeLog() },
                        ),
                    )
                }
                addView(groupGap())
                addView(
                    ctx.toggleRow("待燃补发", "对今天还没点亮的会话隔一段再发同一条，直到点亮或用完轮数", config.retryEnabled) { on ->
                        shell.state.update { it.copy(retryEnabled = on, retryPending = false, retryRound = 0) }
                        shell.rebuild()
                    },
                )
                if (config.retryEnabled) {
                    addView(ctx.divider())
                    addView(
                        ctx.settingRow(
                            Tokens.Icon.REFRESH, "开始补发时间", "从几点起允许补发；之前不补",
                            if (config.retryStartHour >= 0) formatTime(config.retryStartHour, config.retryStartMinute) else "不限（到点就补）",
                            onClick = { pickRetryTime() },
                        ),
                    )
                    addView(ctx.divider())
                    addView(
                        ctx.settingRow(
                            Tokens.Icon.REFRESH, "每次发几条", "同一个会话一次连发几条相同文案",
                            "${config.retryRepeatCount} 条",
                            onClick = { editInt("每次发几条", config.retryRepeatCount, 1, 20) { v -> shell.state.update { it.copy(retryRepeatCount = v) } } },
                        ),
                    )
                    addView(ctx.divider())
                    addView(
                        ctx.settingRow(
                            Tokens.Icon.REFRESH, "补发间隔", "两轮之间隔几分钟（系统最小约 15 分钟）",
                            "${config.retryIntervalMin} 分钟",
                            onClick = { editInt("补发间隔（分钟）", config.retryIntervalMin, 1, 1440) { v -> shell.state.update { it.copy(retryIntervalMin = v) } } },
                        ),
                    )
                    addView(ctx.divider())
                    addView(
                        ctx.settingRow(
                            Tokens.Icon.REFRESH, "最多补发轮数", "每天最多补发几轮，用完就停",
                            "${config.retryMaxRounds} 轮",
                            onClick = { editInt("最多补发轮数", config.retryMaxRounds, 0, 20) { v -> shell.state.update { it.copy(retryMaxRounds = v) } } },
                        ),
                    )
                }
                addView(groupGap())
                addView(
                    ctx.settingRow(
                        "", "任务完成后",
                        "收尾动作：回到运行前的应用 / 关闭抖音 / 留在抖音",
                        afterRunLabel(config.afterRunAction), onClick = { showAfterRunDialog() },
                    ),
                )
                addView(ctx.divider())
                addView(
                    ctx.toggleRow("运行期间静音", "开跑前把媒体/铃声/通知音量降到 0，跑完恢复原音量", config.muteDuringRun) { on ->
                        shell.state.update { it.copy(muteDuringRun = on) }
                    },
                )
            },
        )

        // 数据与工具
        root.addView(secHead("数据与工具"))
        root.addView(
            ctx.card(8).apply {
                addView(
                    ctx.settingRow(
                        Tokens.Icon.BOOK, "运行日志", "抖音进程最近写下的话", "查看",
                        onClick = { showHostLog() },
                    ),
                )
                addView(ctx.divider())
                addView(
                    ctx.settingRow(
                        Tokens.Icon.SHIELD, "系统诊断", "安装包自校验", "正常",
                        onClick = { showSelfCheck() },
                    ),
                )
            },
        )

        // 关于
        root.addView(secHead("关于"))
        root.addView(
            ctx.card(8).apply {
                addView(ctx.settingRow(Tokens.Icon.INFO, "版本", "", versionText()))
                addView(ctx.divider())
                addView(
                    ctx.settingRow(
                        Tokens.Icon.BOOK, "许可与来源", "Apache-2.0", "查看",
                        onClick = { showLicense() },
                    ),
                )
                addView(ctx.divider())
                addView(
                    ctx.settingRow(
                        Tokens.Icon.LINK, "源码", "github.com/tiantong007", "",
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
                addView(ctx.divider())
                addView(
                    ctx.settingRow(Tokens.Icon.BOOK, "使用文档", "", "查看", onClick = { shell.go(Page.DOCS) }),
                )
            },
        )

        root.addView(android.view.View(ctx), ctx.lp(MATCH, 28))
    }

    /** 设置页顶部深色概览卡：自动续火花状态 + 每天几点唤醒 + 调度兜底 + 只在设定时间。 */
    private fun settingsHero(): View {
        val config = shell.state.config
        val on = config.enabled
        val alarmOn = config.alarmHour >= 0
        val tstr = if (alarmOn) formatTime(config.alarmHour, config.alarmMinute) else "—"
        val frame = android.widget.FrameLayout(ctx).apply {
            background = ctx.gradient(Tokens.HERO_START, Tokens.HERO_END, Tokens.R_XL)
            layoutParams = ctx.lp(MATCH, WRAP, top = 10, bottom = 4)
        }
        val body = ctx.column().apply { setPadding(ctx.dp(18), ctx.dp(14), ctx.dp(18), ctx.dp(15)) }
        frame.addView(body, android.widget.FrameLayout.LayoutParams(MATCH, WRAP))
        body.addView(
            ctx.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    View(ctx).apply { background = ctx.shape(if (on) 0xFF35D0A5.toInt() else 0xFF8A8F98.toInt(), 5) },
                    ctx.lp(5, 5, right = 7),
                )
                addView(ctx.text("自动续火花 · " + (if (on) "已开启" else "已关闭"), 11.5f, Tokens.ON_HERO_DIM))
            },
        )
        body.addView(
            ctx.row().apply {
                addView(hstat(tstr, "每天唤醒"), ctx.lp(0, WRAP, 1f))
                addView(hstat(if (alarmOn) "正常" else "未启用", "调度兜底"), ctx.lp(0, WRAP, 1f))
                addView(hstat(if (config.checkAtAlarmOnly) "开" else "关", "只在设定时间"), ctx.lp(0, WRAP, 1f))
            },
            ctx.lp(MATCH, WRAP, top = 10),
        )
        return frame
    }

    private fun hstat(value: String, label: String): View =
        ctx.column().apply {
            addView(ctx.text(value, 18f, Tokens.ON_HERO, bold = true))
            addView(ctx.text(label, 10.5f, Tokens.ON_HERO_FAINT), ctx.lp(WRAP, WRAP, top = 2))
        }

    /** 分组标题（带可选「?」说明）。 */
    private fun groupGap(): View =
        View(ctx).apply { layoutParams = ctx.lp(MATCH, ctx.dp(10)) }

    private fun secHead(title: String, tip: String = ""): View =
        ctx.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ctx.lp(MATCH, WRAP, top = 18, bottom = 8)
            setPadding(ctx.dp(4), 0, ctx.dp(4), 0)
            addView(ctx.text(title, 14.5f, Tokens.INK, bold = true), ctx.lp(0, WRAP, 1f))
            if (tip.isNotBlank()) {
                addView(
                    ctx.text("?", 11f, Tokens.INK_FAINT, bold = true).apply {
                        gravity = Gravity.CENTER
                        background = ctx.shape(Tokens.SEG_BG, 9)
                        isClickable = true
                        setOnClickListener { showTip(title, tip) }
                    },
                    ctx.lp(18, 18),
                )
            }
        }

    private fun showTip(title: String, tip: String) {
        android.app.AlertDialog.Builder(ctx).setTitle(title).setMessage(tip)
            .setPositiveButton("知道了", null).show()
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

    private fun afterRunLabel(action: String): String = when (action) {
        "kill" -> "关闭抖音"
        "keep" -> "留在抖音"
        else -> "回到原应用"
    }

    private fun showAfterRunDialog() {
        val labels = arrayOf(
            "回到运行前的应用（推荐）\n运行前在哪个 App，跑完就切回哪个；运行前息屏则只关抖音、不点亮",
            "关闭抖音\n跑完只把抖音关掉，停在桌面",
            "留在抖音\n跑完不动，抖音留在前台",
        )
        val keys = arrayOf("restore", "kill", "keep")
        val cur = keys.indexOf(shell.state.config.afterRunAction).coerceAtLeast(0)
        android.app.AlertDialog.Builder(ctx)
            .setTitle("任务完成后")
            .setSingleChoiceItems(labels, cur) { dialog, which ->
                shell.state.update { it.copy(afterRunAction = keys[which]) }
                dialog.dismiss()
                shell.rebuild()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickRetryTime() {
        val c = shell.state.config
        val h = if (c.retryStartHour < 0) 22 else c.retryStartHour
        val m = if (c.retryStartHour < 0) 0 else c.retryStartMinute
        runCatching {
            val dialog = TimePickerDialog(
                shell.activity,
                { _, hh, mm ->
                    shell.state.update { it.copy(retryStartHour = hh, retryStartMinute = mm) }
                    shell.rebuild()
                    ctx.tip("从 ${formatTime(hh, mm)} 起补发")
                },
                h,
                m,
                true,
            )
            // 「不限」：不设起始时刻，一到点就允许补发
            dialog.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "不限") { _, _ ->
                shell.state.update { it.copy(retryStartHour = -1) }
                shell.rebuild()
                ctx.tip("不限起始时间，到点就补")
            }
            dialog.show()
        }
    }

    private fun editInt(title: String, cur: Int, lo: Int, hi: Int, onSet: (Int) -> Unit) {
        val input =
            android.widget.EditText(ctx).apply {
                setText(cur.toString())
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setSelection(text.length)
            }
        android.app.AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val v = input.text.toString().toIntOrNull()?.coerceIn(lo, hi)
                if (v != null) {
                    onSet(v)
                    shell.rebuild()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── 定时唤醒：权限检测与唤醒日志 ─────────────────────────────────

    private fun exactAlarmOk(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
        } else {
            true
        }

    private fun batteryOk(): Boolean =
        (ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
            .isIgnoringBatteryOptimizations(ctx.packageName)

    private fun alarmPermSummary(): String {
        val missing = ArrayList<String>()
        if (!exactAlarmOk()) missing.add("精确闹钟")
        if (!batteryOk()) missing.add("电池不限制")
        return if (missing.isEmpty()) "已就绪" else "还需：" + missing.joinToString("、")
    }

    private fun openSys(intent: android.content.Intent) {
        runCatching { ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun showAlarmPerm() {
        val pkg = "package:" + ctx.packageName
        com.tiantong.spark.app.ui.v2.comp.Sheet.open(shell.activity, "定时唤醒权限") { body ->
            body.addView(
                ctx.note(
                    "为确保到点能唤醒，建议开启下面各项。点右侧「去开启」跳到系统设置，开不开由你自己决定。",
                    Tokens.WARN,
                    Tokens.WARN_SOFT,
                ),
            )
            fun rowPrem(title: String, sub: String, ok: Boolean, action: android.content.Intent?) {
                var click: (() -> Unit)? = null
                if (!ok && action != null) {
                    val a = action
                    click = { openSys(a) }
                }
                body.addView(
                    ctx.settingRow(
                        if (ok) Tokens.Icon.CHECK else Tokens.Icon.REFRESH,
                        title,
                        sub,
                        if (ok) "已开启" else if (action != null) "去开启" else "按需",
                        glyphColor = if (ok) 0xFF35D0A5.toInt() else Tokens.INK_DIM,
                        onClick = click,
                    ),
                )
            }
            rowPrem(
                "精确闹钟", "到点准时触发（Android 12+ 需授权）", exactAlarmOk(),
                android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse(pkg)),
            )
            rowPrem(
                "电池不限制", "防止后台被省电策略冻结", batteryOk(),
                android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse(pkg)),
            )
            rowPrem(
                "自启动 / 后台", "部分国产 ROM 需在系统里允许自启动", false,
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse(pkg)),
            )
            rowPrem(
                "锁定任务", "最近任务里锁定，防一键清理", false,
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse(pkg)),
            )
        }
    }

    private fun wakeSummary(): String {
        val entries = WakeLog.entries(shell.state.config)
        if (entries.isEmpty()) return "还没有记录"
        val pre = entries.count { it.method == DailyAlarm.LaunchMethod.PRELOAD }
        val mon = entries.count { it.method == DailyAlarm.LaunchMethod.MONKEY }
        val fail = entries.count { it.method == DailyAlarm.LaunchMethod.FAILED }
        return "共 ${entries.size} 次 · 免root $pre / root $mon / 失败 $fail"
    }

    private fun showWakeLog() {
        val entries = WakeLog.entries(shell.state.config).asReversed()
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        val body = if (entries.isEmpty()) {
            "还没有唤醒记录。"
        } else {
            entries.joinToString("\n") { e ->
                fmt.format(java.util.Date(e.at)) + "  " + when (e.method) {
                    DailyAlarm.LaunchMethod.PRELOAD -> "免 root（preload）"
                    DailyAlarm.LaunchMethod.MONKEY -> "root + monkey"
                    DailyAlarm.LaunchMethod.FAILED -> "失败"
                }
            }
        }
        com.tiantong.spark.app.ui.v2.comp.Sheet.open(shell.activity, "唤醒日志") { b ->
            b.addView(ctx.text(wakeSummary(), Tokens.T_SUB, Tokens.INK_DIM))
            b.addView(
                ctx.mono(body, 12f, Tokens.INK).apply { setLineSpacing(ctx.dpF(4f), 1f) },
                ctx.lp(MATCH, WRAP, top = 8),
            )
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
