package com.tiantong.spark.app.ui.v2.screen

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.tiantong.spark.app.config.model.ConversationSnapshot
import com.tiantong.spark.app.ui.common.AvatarLoader
import com.tiantong.spark.app.ui.v2.comp.MATCH
import com.tiantong.spark.app.ui.v2.comp.WRAP
import com.tiantong.spark.app.ui.v2.comp.card
import com.tiantong.spark.app.ui.v2.comp.column
import com.tiantong.spark.app.ui.v2.comp.divider
import com.tiantong.spark.app.ui.v2.comp.dp
import com.tiantong.spark.app.ui.v2.comp.dpF
import com.tiantong.spark.app.ui.v2.comp.emptyState
import com.tiantong.spark.app.ui.v2.comp.gradient
import com.tiantong.spark.app.ui.v2.comp.icon
import com.tiantong.spark.app.ui.v2.comp.lp
import com.tiantong.spark.app.ui.v2.comp.row
import com.tiantong.spark.app.ui.v2.comp.shape
import com.tiantong.spark.app.ui.v2.comp.switchChip
import com.tiantong.spark.app.ui.v2.comp.text
import com.tiantong.spark.app.ui.v2.nav.PageView
import com.tiantong.spark.app.ui.v2.nav.Shell
import com.tiantong.spark.app.ui.v2.state.Membership
import com.tiantong.spark.app.ui.v2.theme.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 状态看板：今天还有谁没续、谁续过了，以及「自动发送名单」里都有谁。
 *
 * 层级：深色进度卡（自动人数 / 已续 / 已燃 + 名单构成 + 下一轮）→ 第 1 行五档分段（火花状态）
 * → 第 2 行两档分段（自动发送 / 不发送，与第 1 行互斥）→ 名单（两行结构：名字 + 火花天数，
 * 三态徽章 + 状态徽章 + 说明）→ 底部说明卡。
 *
 * 行尾的控件按列表来源分工：五档看板里是勾选开关（手动发谁），
 * 名单两档里是规则按钮（固定发送 / 固定不发 / 恢复默认），互不重叠。
 */
class HomeScreen(private val shell: Shell) : PageView {

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
        root.addView(hero())
        root.addView(segmentRow1())
        root.addView(segmentRow2())
        root.addView(listOfConversations())
        root.addView(explainCard())
    }

    // ── 深色进度卡 ────────────────────────────────────────────────

    private fun hero(): View {
        val state = shell.state
        val autoRows = state.autoRows()
        val autoN = autoRows.size
        val fixedIn = state.fixedSendCount()
        val noN = state.noSendCount()
        // 名单为空时退回全部会话，避免 hero 三个数字全为 0。
        val statRows = if (autoN > 0) autoRows else state.visible()
        val done = statRows.count { state.sentToday(it.conversationId) }
        val lit = statRows.count { it.state == 1 }

        val frame = FrameLayout(ctx).apply {
            background = ctx.gradient(Tokens.HERO_START, Tokens.HERO_END, Tokens.R_XL)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, ctx.dpF(Tokens.R_XL.toFloat()))
                }
            }
            layoutParams = ctx.lp(MATCH, WRAP, top = 10, bottom = 12)
        }

        // 右上角红光晕，跟原型的一个意思。
        val glow = ctx.dp(170)
        frame.addView(
            View(ctx).apply { background = GlowDrawable(Tokens.ACCENT) },
            FrameLayout.LayoutParams(glow, glow).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = -ctx.dp(50)
                rightMargin = -ctx.dp(40)
            },
        )

        val body = ctx.column().apply {
            setPadding(ctx.dp(18), ctx.dp(12), ctx.dp(18), ctx.dp(15))
        }
        frame.addView(body, FrameLayout.LayoutParams(MATCH, WRAP))

        // 记录时间：最近一次抓到快照的时间，代替原来那句「今天」。
        body.addView(
            ctx.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                val online = state.hostOnline()
                addView(
                    View(ctx).apply {
                        background = ctx.shape(if (online) 0xFF35D0A5.toInt() else 0xFF8A8F98.toInt(), 5)
                    },
                    ctx.lp(5, 5, right = 7),
                )
                addView(ctx.text("记录时间 ${recordTime()} · 抖音" + if (online) "在线" else "离线", 11.5f, Tokens.ON_HERO_DIM))
            },
        )

        // 三列等宽、左中右贴边：自动发送（左）/ 已续（中）/ 已燃（右）。
        // 三处数字同字号（26sp），只报绝对数不摆分母 —— 分母一出现（16？22？）
        // 就要解释口径，而口径本身是另一件事（名单构成已经在小字行里说了）。
        body.addView(
            ctx.row().apply {
                addView(heroStat("$autoN", "自动发送", Gravity.START), ctx.lp(0, WRAP, 1f))
                addView(heroStat("$done", "已续", Gravity.CENTER_HORIZONTAL), ctx.lp(0, WRAP, 1f))
                addView(heroStat("$lit", "已燃", Gravity.END), ctx.lp(0, WRAP, 1f))
            },
            ctx.lp(MATCH, WRAP, top = 10),
        )

        // 小字统计行：名单构成。
        body.addView(
            ctx.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(stat("固定发送", fixedIn), ctx.lp(WRAP, WRAP, right = 14))
                addView(stat("有火花", autoN - fixedIn), ctx.lp(WRAP, WRAP, right = 14))
                addView(stat("不会续", noN), ctx.lp(WRAP, WRAP))
            },
            ctx.lp(MATCH, WRAP, top = 8),
        )

        // 开关状态 + 下一轮：原来三个胶囊里最重要的两条，收成一行小字。
        val hint = (if (state.config.enabled) "自动续火花已开启" else "自动续火花已关闭") +
            " · 下一轮 ${nextRound()}"
        body.addView(ctx.text(hint, 11.5f, 0x80FFFFFF.toInt()), ctx.lp(MATCH, WRAP, top = 4))
        return frame
    }

    /**
     * hero 的一格：26sp 数字 + 右下角小字标签，整格按 [gravity] 贴左 / 居中 / 贴右。
     *
     * 三格权重都是 1f，左中右各占三分之一，所以三个数字永远贴在卡片两端与正中。
     */
    private fun heroStat(value: String, suffix: String, gravity: Int = Gravity.CENTER_HORIZONTAL): View =
        ctx.column().apply {
            this.gravity = gravity
            addView(
                ctx.row().apply {
                    this.gravity = Gravity.BOTTOM
                    addView(ctx.text(value, 26f, Tokens.ON_HERO, bold = true))
                    addView(
                        ctx.text(suffix, 11.5f, Tokens.ON_HERO_FAINT, bold = true),
                        ctx.lp(WRAP, WRAP, left = 3, bottom = 3),
                    )
                },
                ctx.lp(WRAP, WRAP),
            )
        }

    /** 小字统计项：灰色标签 + 白色数字。 */
    private fun stat(label: String, value: Int): View =
        ctx.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ctx.text(label, 11.5f, Tokens.ON_HERO_FAINT))
            addView(ctx.text(" $value", 11.5f, Tokens.ON_HERO, bold = true), ctx.lp(WRAP, WRAP, left = 2))
        }

    private fun recordTime(): String =
        if (shell.state.recordedAt() <= 0) "还没抓到" else
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(shell.state.recordedAt()))

    private fun nextRound(): String {
        val hour = shell.state.config.alarmHour
        return if (hour < 0) "未设" else String.format(Locale.US, "%02d:%02d", hour, shell.state.config.alarmMinute)
    }

    // ── 两行分段 ──────────────────────────────────────────────────

    /** 第一行：火花状态五档。点它等于「退出名单视图」。 */
    private fun segmentRow1(): View {
        val state = shell.state
        val labels = listOf(
            "全部 ${state.visible().size}",
            "已燃 ${state.litCount()}",
            "待燃 ${state.waitingCount()}",
            "待续 ${state.todoCount()}",
            "无火花 ${state.deadCount()}",
        )
        val selected = if (state.mstat >= 0) -1 else state.filter
        return seg(labels, selected, top = 16, bottom = 5) { index ->
            state.filter = index
            state.mstat = -1
            shell.rebuild()
        }
    }

    /** 第二行：发送名单两档。再点一次同一档退回五档。 */
    private fun segmentRow2(): View {
        val state = shell.state
        val labels = listOf("自动发送 ${state.autoCount()}", "不发送 ${state.noSendCount()}")
        return seg(labels, state.mstat, top = 0, bottom = 12) { index ->
            state.mstat = if (state.mstat == index) -1 else index
            shell.rebuild()
        }
    }

    /**
     * 分段控制器：一排等宽胶囊，选中的浮起成白底。
     *
     * [selected] 传 -1 表示这一行当前不生效（另一行在生效）——两行互斥就靠这个。
     */
    private fun seg(
        labels: List<String>,
        selected: Int,
        top: Int,
        bottom: Int,
        onPick: (Int) -> Unit,
    ): LinearLayout =
        ctx.row().apply {
            layoutParams = ctx.lp(MATCH, WRAP, top = top, bottom = bottom)
            setPadding(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
            background = ctx.shape(Tokens.SEG_BG, Tokens.R_MD)
            labels.forEachIndexed { index, label ->
                val on = index == selected
                addView(
                    ctx.text(label, 13f, if (on) Tokens.INK else Tokens.INK_DIM, bold = on).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, ctx.dp(7), 0, ctx.dp(7))
                        if (on) {
                            background = ctx.shape(Tokens.SURFACE, 9)
                            elevation = ctx.dpF(1.5f)
                        }
                        isClickable = true
                        setOnClickListener { onPick(index) }
                    },
                    ctx.lp(0, WRAP, 1f),
                )
            }
        }

    // ── 名单 ──────────────────────────────────────────────────────

    private fun listOfConversations(): View {
        val state = shell.state
        val member = state.mstat >= 0
        val items = state.listRows()
        val box = ctx.column().apply { background = ctx.shape(Tokens.SURFACE, 18) }

        val ghosts = state.ghostsFor(state.mstat)
        if (items.isEmpty() && ghosts.isEmpty()) {
            box.addView(emptyStateFor())
            return box
        }

        val persons = items.filter { !it.isGroup }
        val groups = items.filter { it.isGroup }
        var sectionStarted = false

        fun addSection(title: String, arr: List<ConversationSnapshot>, collapsed: Boolean, onToggle: () -> Unit) {
            if (arr.isEmpty()) return
            if (sectionStarted) box.addView(ctx.divider())
            sectionStarted = true
            box.addView(sectionHeader(title, arr.size, collapsed, onToggle))
            if (collapsed) return
            arr.forEach { snapshot ->
                box.addView(ctx.divider())
                box.addView(rowOf(snapshot, member))
            }
        }

        // 好友在上、群聊在下；点段头折叠，只影响显示，不影响勾选与全选。
        addSection("好友", persons, state.gsecPerson) {
            state.gsecPerson = !state.gsecPerson
            shell.rebuild()
        }
        addSection("群聊", groups, state.gsecGroup) {
            state.gsecGroup = !state.gsecGroup
            shell.rebuild()
        }

        // 失联条目：名单里指向已不存在的会话。不静默丢，灰行摆出来让用户自己清。
        if (ghosts.isNotEmpty()) {
            if (items.isNotEmpty()) box.addView(ctx.divider())
            ghosts.forEachIndexed { index, (id, mode) ->
                box.addView(ghostRow(id, mode))
                if (index != ghosts.lastIndex) box.addView(ctx.divider())
            }
        }
        return box
    }

    /** 群/好友分段头：▾/▸ + 标题 + 人数，整行可点折叠。 */
    private fun sectionHeader(title: String, count: Int, collapsed: Boolean, onToggle: () -> Unit): View =
        ctx.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(15), ctx.dp(12), ctx.dp(15), ctx.dp(8))
            isClickable = true
            setOnClickListener { onToggle() }
            addView(
                ctx.text(if (collapsed) "▸" else "▾", 13.5f, Tokens.INK_DIM, bold = true),
                ctx.lp(WRAP, WRAP, right = 7),
            )
            addView(ctx.text(title, 14.5f, Tokens.INK, bold = true), ctx.lp(0, WRAP, 1f))
            addView(ctx.text("$count", 12.5f, Tokens.INK_FAINT, bold = true), ctx.lp(WRAP, WRAP))
        }

    /** 失联名单行：id 已不在会话列表里，只留着用户设过的归属。 */
    private fun ghostRow(id: String, mode: Membership): View {
        val state = shell.state
        return ctx.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(15), ctx.dp(12), ctx.dp(15), ctx.dp(12))
            minimumHeight = ctx.dp(66)
            addView(
                TextView(ctx).apply {
                    text = "?"
                    gravity = Gravity.CENTER
                    setTextColor(Tokens.INK_FAINT)
                    textSize = 15f
                    background = ctx.shape(0xFFF1F2F5.toInt(), 21)
                },
                ctx.lp(42, 42, right = 12),
            )
            val who = ctx.column()
            who.addView(
                ctx.text("已失联的会话", 14.5f, Tokens.INK_DIM, bold = true).apply { maxLines = 1 },
                ctx.lp(MATCH, WRAP),
            )
            val sub = ctx.row().apply { gravity = Gravity.CENTER_VERTICAL }
            sub.addView(
                tag(
                    mode.label,
                    if (mode == Membership.RENEW) Tokens.OK else 0xFF33363B.toInt(),
                    if (mode == Membership.RENEW) 0x1712B886 else 0x0B000000,
                ),
                ctx.lp(WRAP, WRAP, right = 5),
            )
            sub.addView(tag("已失联", Tokens.MUTED, Tokens.MUTED_SOFT), ctx.lp(WRAP, WRAP, right = 5))
            sub.addView(
                ctx.text("· 尾号 " + id.takeLast(6), 12f, Tokens.INK_DIM).apply { maxLines = 1 },
                ctx.lp(0, WRAP, 1f, left = 0),
            )
            who.addView(sub, ctx.lp(MATCH, WRAP, top = 4))
            addView(who, ctx.lp(0, WRAP, 1f))
            addView(
                ruleButton("恢复默认", Tokens.OK, 0x0F12B886) {
                    state.setMembership(id, Membership.AUTO)
                    state.touchedId = null
                    shell.rebuild()
                },
                ctx.lp(WRAP, WRAP),
            )
        }
    }

    private fun emptyStateFor(): View {
        val state = shell.state
        return when {
            state.mstat == 0 && state.autoCount() == 0 ->
                ctx.emptyState(Tokens.Icon.CHECK, "自动发送名单是空的", "所有会话都被固定不发了吗")

            state.mstat == 1 && state.noSendCount() == 0 ->
                ctx.emptyState(Tokens.Icon.CHECK, "不发送名单是空的", "所有人都还会自动续火花")

            state.visible().isEmpty() ->
                ctx.emptyState(Tokens.Icon.INFO, "还没有会话数据", "进一次抖音，回来点右上角刷新")

            state.filter == 3 && state.todoCount() == 0 ->
                ctx.emptyState(Tokens.Icon.CHECK, "今天都续完了", "该续的 ${state.renewable().size} 个都已经发过，明天再来")

            state.filter == 4 ->
                ctx.emptyState(Tokens.Icon.FIRE, "没有无火花的会话", "所有会话的火花都还在")

            else -> ctx.emptyState(Tokens.Icon.SEARCH, "这一档没有人", "换一个查看条件试试")
        }
    }

    /**
     * 一行会话。两行结构：
     * ① 名字 + 火花天数；② 三态徽章 + 状态徽章 + 无文案 + 说明。
     * 行尾：[member] 为真挂名单规则按钮，否则挂勾选开关。
     */
    private fun rowOf(snapshot: ConversationSnapshot, member: Boolean): View {
        val state = shell.state
        val id = snapshot.conversationId

        return ctx.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(15), ctx.dp(12), ctx.dp(15), ctx.dp(12))
            minimumHeight = ctx.dp(66)
            // 刚改过归属的那一行浅浅标一下：整页重建后一眼能找到人跑到哪去了。
            if (state.touchedId == id) background = ctx.shape(0x0F12B886, 18)
            isClickable = true
            setOnClickListener { shell.openEditor(conversationId = id) }

            addView(
                ImageView(ctx).apply {
                    AvatarLoader.load(ctx, this, snapshot.peerUid.ifBlank { id }, snapshot.displayName, 42)
                },
                ctx.lp(42, 42, right = 12),
            )

            val who = ctx.column()

            // ① 名字 + 火花天数（天数和名字同字号，火苗 12）
            val nameRow = ctx.row().apply { gravity = Gravity.CENTER_VERTICAL }
            nameRow.addView(
                ctx.text(snapshot.displayName, 14.5f, Tokens.INK, bold = true).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                ctx.lp(WRAP, WRAP, right = 6),
            )
            if (snapshot.hasSpark) {
                nameRow.addView(ctx.icon(Tokens.Icon.FIRE, 12f, Tokens.ACCENT), ctx.lp(WRAP, WRAP, right = 3))
                nameRow.addView(ctx.text("${snapshot.days}", 14.5f, Tokens.INK, bold = true))
                nameRow.addView(ctx.text("天", 14.5f, Tokens.INK, bold = true))
            }
            if (snapshot.isGroup) nameRow.addView(tag("群", Tokens.INFO, Tokens.INFO_SOFT), ctx.lp(WRAP, WRAP, left = 6))
            who.addView(nameRow, ctx.lp(MATCH, WRAP))

            // ② 三态徽章 + 状态徽章 + 无文案 + 说明（没文案时不写说明，省略解释移到底部说明卡）
            val subRow = ctx.row().apply { gravity = Gravity.CENTER_VERTICAL }
            subRow.addView(memberTag(snapshot), ctx.lp(WRAP, WRAP, right = 5))
            subRow.addView(statusTag(snapshot), ctx.lp(WRAP, WRAP))
            if (state.noTextNeeded(snapshot)) {
                subRow.addView(tag("无文案", Tokens.DANGER, 0x1AE5484D), ctx.lp(WRAP, WRAP, left = 5))
            }
            val sub = subtitle(snapshot)
            if (sub.isNotBlank()) {
                subRow.addView(
                    ctx.text("· $sub", 12f, Tokens.INK_DIM).apply {
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    },
                    ctx.lp(0, WRAP, 1f, left = 5),
                )
            }
            who.addView(subRow, ctx.lp(MATCH, WRAP, top = 4))
            addView(who, ctx.lp(0, WRAP, 1f))

            // 名单档位也要能勾：全选/发送作用的是当前列表；行尾顺序与网页一致：规则按钮在内，勾选开关永远是最外侧最后一个。
            if (member) addView(rules(snapshot), ctx.lp(WRAP, WRAP, right = 8))
            addView(
                ctx.switchChip(state.isPicked(id)) { on ->
                    if (state.isPicked(id) != on) state.togglePick(id)
                    shell.rebuild()
                },
                ctx.lp(44, 26),
            )
        }
    }

    /** 名单行尾的规则按钮：默认态给「固定发送 / 固定不发」，固定后给「恢复默认 + 另一个」。竖排，最多两个。 */
    private fun rules(snapshot: ConversationSnapshot): View {
        val state = shell.state
        val id = snapshot.conversationId
        val mode = state.membershipOf(id)
        val inList = state.inAutoList(snapshot)
        val box = ctx.column()

        if (mode != Membership.AUTO) {
            box.addView(
                ruleButton("恢复默认", Tokens.OK, 0x0F12B886) {
                    state.setMembership(id, Membership.AUTO)
                    state.touchedId = id
                    shell.rebuild()
                },
                ctx.lp(MATCH, WRAP, bottom = 4),
            )
        }
        if (inList && mode == Membership.AUTO) {
            // 默认态、且在自动发送名单里：也给「固定发送」，不再只有「固定不发」
            box.addView(
                ruleButton("固定发送", Tokens.ACCENT, 0x0EFE2C55) {
                    state.setMembership(id, Membership.RENEW)
                    state.touchedId = id
                    shell.rebuild()
                },
                ctx.lp(MATCH, WRAP, bottom = 4),
            )
        }
        if (inList) {
            box.addView(
                ruleButton("固定不发", 0xFF33363B.toInt(), 0x0B000000) {
                    state.setMembership(id, Membership.SKIP)
                    state.touchedId = id
                    shell.rebuild()
                },
                ctx.lp(MATCH, WRAP),
            )
        } else {
            box.addView(
                ruleButton("固定发送", Tokens.ACCENT, 0x0EFE2C55) {
                    state.setMembership(id, Membership.RENEW)
                    state.touchedId = id
                    shell.rebuild()
                },
                ctx.lp(MATCH, WRAP),
            )
        }
        return box
    }

    /** 规则按钮：浅底 + 同色文字 + 同色描边，跟徽章一套配色。 */
    private fun ruleButton(label: String, color: Int, tint: Int, onClick: () -> Unit): TextView =
        ctx.text(label, 11f, color, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(ctx.dp(9), ctx.dp(4), ctx.dp(9), ctx.dp(4))
            background = ctx.shape(tint, 8).apply {
                setStroke(ctx.dp(1), (color and 0x00FFFFFF) or 0x42000000)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    /** 三态徽章：默认 / 固定发送 / 固定不发。 */
    private fun memberTag(snapshot: ConversationSnapshot): TextView =
        when (shell.state.membershipOf(snapshot.conversationId)) {
            Membership.RENEW -> tag("固定发送", Tokens.OK, 0x1712B886)
            Membership.SKIP -> tag("固定不发", 0xFF33363B.toInt(), 0x12000000)
            Membership.AUTO -> tag("默认", Tokens.INK_FAINT, 0x0B000000)
        }

    /**
     * 行内状态标签，和首页五档一一对应：
     * 无火花 / 可点亮 / 重燃中 / 待燃 / 待续 / 已燃。
     */
    private fun statusTag(snapshot: ConversationSnapshot): TextView {
        val state = shell.state
        val sent = state.sentToday(snapshot.conversationId)
        return when {
            !snapshot.hasSpark && snapshot.stateText == "可点亮" -> tag("可点亮", Tokens.INFO, Tokens.INFO_SOFT)
            !snapshot.hasSpark -> tag("无火花", Tokens.MUTED, Tokens.MUTED_SOFT)
            snapshot.state == 1 -> tag("已燃", Tokens.OK, Tokens.OK_SOFT)
            snapshot.state == 3 -> tag("重燃中", 0xFFB45309.toInt(), Tokens.WARN_SOFT)
            sent -> tag("待燃", Tokens.WARN, Tokens.WARN_SOFT)
            else -> tag("待续", Tokens.WARN, Tokens.WARN_SOFT)
        }
    }

    private fun tag(label: String, color: Int, tint: Int): TextView =
        ctx.text(label, 10.5f, color, bold = true).apply {
            setPadding(ctx.dp(7), ctx.dp(2), ctx.dp(7), ctx.dp(2))
            background = ctx.shape(tint, 6)
        }

    /** 第二行说明：只放状态类信息（火花异常 / 到期 / 需几天），天数不在这里重复。 */
    /**
     * 副标题只说"补充信息"，状态已经由 [statusTag] 表达，这里绝不重复状态词。
     *
     * - 重燃中：只有宿主给了带进度的原生文案（含 `/`，如「重燃中 2/3」）才显示。
     *   `recoverDays` / `recoverNeedDays` 是时间轴上的预测值，实测方向会反过来
     *   （例：李金鹏 rd=3/rn=1，一一大王 rd=1/rn=3），拿去拼进度会和宿主界面矛盾，所以不用。
     * - 到期提示：宿主给的「6 天后消失」这类未来段文案，前面补主语「火花」。
     * - `stateText` 在 state=1/2 时是**天数数字**、state=3 时是**状态词**，语义不稳定，一律不当文案用。
     */
    private fun subtitle(snapshot: ConversationSnapshot): String {
        if (!shell.state.hasScript(snapshot.conversationId)) return ""
        val parts = mutableListOf<String>()
        // 重燃中的进度（如 1/3）已经并在状态徽章里，这里不再重复。
        if (snapshot.expireText.isNotBlank()) parts += "火花${snapshot.expireText}"
        return parts.joinToString(" · ")
    }

    /** 底部说明卡：名单四种归属各一句 + 一句「跟今天发没发过无关」。始终渲染。 */
    private fun explainCard(): View {
        val box = ctx.card()
        box.addView(ctx.text("说明", Tokens.T_SECTION, Tokens.INK_DIM, bold = true))
        val lines = listOf(
            "无文案：没写脚本、也没挂宏的会话（模板正文不参与判定），自动发送会跳过，主页手动勾选发送同样跳过。",
            "默认：只要还有火花（已燃 / 待燃 / 重燃中）就在名单里，每天自动续一次 —— 已燃只是今天续过了，明天照样续。",
            "固定发送：没火花也每天发 —— 想从头点亮没火花的人、或者已燃的大火花想额外兜底，都用这个。",
            "固定不发：永远不自动续，即使有火花、明天掉到待燃也不发；点「恢复默认」放回默认。",
            "自动发送名单只按火花和固定归属判定，跟今天发没发过无关，也不影响主页手动勾选发送。",
        )
        lines.forEach { line ->
            box.addView(
                ctx.text(line, 12f, Tokens.INK_DIM).apply { setLineSpacing(ctx.dpF(3f), 1f) },
                ctx.lp(MATCH, WRAP, top = 6),
            )
        }
        return box
    }
}

/** 右上角的红色光晕，画一个径向渐变圆。 */
private class GlowDrawable(private val color: Int) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }

    override fun draw(canvas: Canvas) {
        val r = bounds.width().coerceAtMost(bounds.height()) / 2f
        if (r <= 0f) return
        paint.shader = RadialGradient(
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            r,
            intArrayOf((color and 0x00FFFFFF) or 0x8C000000.toInt(), color and 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), r, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
