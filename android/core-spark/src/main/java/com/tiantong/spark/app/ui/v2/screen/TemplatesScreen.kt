package com.tiantong.spark.app.ui.v2.screen

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.tiantong.spark.app.config.BuiltinTemplates
import com.tiantong.spark.app.config.model.TemplateGroup
import com.tiantong.spark.app.ui.v2.comp.MATCH
import com.tiantong.spark.app.ui.v2.comp.WRAP
import com.tiantong.spark.app.ui.v2.comp.badge
import com.tiantong.spark.app.ui.v2.comp.card
import com.tiantong.spark.app.ui.v2.comp.column
import com.tiantong.spark.app.ui.v2.comp.dp
import com.tiantong.spark.app.ui.v2.comp.emptyState
import com.tiantong.spark.app.ui.v2.comp.icon
import com.tiantong.spark.app.ui.v2.comp.lp
import com.tiantong.spark.app.ui.v2.comp.mono
import com.tiantong.spark.app.ui.v2.comp.row
import com.tiantong.spark.app.ui.v2.comp.shape
import com.tiantong.spark.app.ui.v2.comp.sectionLabel
import com.tiantong.spark.app.ui.v2.comp.text
import com.tiantong.spark.app.ui.v2.comp.miniButton
import com.tiantong.spark.app.ui.v2.comp.tip
import com.tiantong.spark.app.ui.v2.nav.PageView
import com.tiantong.spark.app.ui.v2.nav.Shell
import com.tiantong.spark.app.ui.v2.sheet.PreviewSheet
import com.tiantong.spark.app.ui.v2.theme.Tokens
import java.util.UUID
import com.tiantong.spark.app.ui.v2.comp.dpF

/**
 * 模板页：内置的两条起点在上，自己的模板在下。
 *
 * 每张卡就是「一个模板 + 谁在用它 + 几个动作」。不做展开折叠，
 * 动作直接摆在卡片底部，点一下就发生。
 */
class TemplatesScreen(private val shell: Shell) : PageView {

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

        root.addView(ctx.sectionLabel("内置模板"))
        BuiltinTemplates.ALL.forEach { builtin ->
            root.addView(
                ctx.card(14).apply {
                    addView(
                        ctx.row().apply {
                            addView(ctx.icon(Tokens.Icon.FIRE, 16f, Tokens.ACCENT), ctx.lp(24, WRAP))
                            addView(ctx.text(builtin.name, Tokens.T_BODY, Tokens.INK, bold = true), ctx.lp(0, WRAP, 1f))
                            addView(ctx.badge("内置", Tokens.INK_DIM, Tokens.MUTED_SOFT), ctx.lp(WRAP, WRAP))
                        },
                        ctx.lp(MATCH, WRAP),
                    )
                    addView(ctx.text(builtin.desc, Tokens.T_TINY, Tokens.INK_DIM), ctx.lp(MATCH, WRAP, top = 4))
                    val builtinPreview = builtin.body.lineSequence().filter { it.isNotBlank() }.take(3).joinToString("\n")
                    addView(
                        ctx.mono(builtinPreview.ifBlank { "（空模板）" }, 12f, Tokens.EDITOR_TEXT).apply {
                            maxLines = 3
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setLineSpacing(ctx.dpF(2f), 1f)
                            setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
                            background = ctx.shape(Tokens.EDITOR_BG, 14)
                        },
                        ctx.lp(MATCH, WRAP, top = 8),
                    )
                    addView(
                        ctx.row().apply {
                            gravity = Gravity.END
                            addView(
                                ctx.miniButton("用这个建模板") { createFrom(builtin.name, builtin.body) },
                                ctx.lp(WRAP, WRAP),
                            )
                            addView(
                                ctx.miniButton("按条预览") {
                                    PreviewSheet.open(shell.activity, builtin.name, builtin.body, null)
                                },
                                ctx.lp(WRAP, WRAP, left = 4),
                            )
                        },
                        ctx.lp(MATCH, WRAP, top = 2),
                    )
                },
            )
        }

        root.addView(
            ctx.row().apply {
                addView(ctx.sectionLabel("我的模板（${config.groups.size}）"), ctx.lp(0, WRAP, 1f))
                addView(
                    ctx.text("＋ 新建", Tokens.T_SUB, Tokens.ACCENT, bold = true).apply {
                        setPadding(ctx.dp(8), ctx.dp(18), ctx.dp(4), ctx.dp(8))
                        isClickable = true
                        setOnClickListener { createTemplate() }
                    },
                    ctx.lp(WRAP, WRAP),
                )
            },
            ctx.lp(MATCH, WRAP),
        )

        if (config.groups.isEmpty()) {
            root.addView(
                ctx.emptyState(Tokens.Icon.TEMPLATE, "还没有自己的模板", "上面的内置模板点「用这个建模板」就会出现在这里"),
                ctx.lp(MATCH, WRAP),
            )
            return
        }

        config.groups.forEach { group -> root.addView(templateCard(group)) }
    }

    private fun templateCard(group: TemplateGroup): View {
        val config = shell.state.config
        val members = config.membersOf(group.id)
        val card = ctx.card(14)

        card.addView(
            ctx.row().apply {
                addView(
                    ctx.text(group.name.ifBlank { "未命名模板" }, Tokens.T_BODY, Tokens.INK, bold = true)
                        .apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                    ctx.lp(0, WRAP, 1f),
                )
                addView(
                    ctx.badge("${members.size} 个会话", Tokens.INK_DIM, Tokens.MUTED_SOFT),
                    ctx.lp(WRAP, WRAP, left = 6),
                )
            },
            ctx.lp(MATCH, WRAP),
        )

        val preview = group.body.lineSequence().filter { it.isNotBlank() }.take(3).joinToString("\n")
        card.addView(
            ctx.mono(preview.ifBlank { "（空模板）" }, 12f, Tokens.EDITOR_TEXT).apply {
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setLineSpacing(ctx.dpF(2f), 1f)
                setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
                background = ctx.shape(Tokens.EDITOR_BG, 14)
            },
            ctx.lp(MATCH, WRAP, top = 8),
        )

        // 五个动作平铺：编辑 → 预览 → 克隆 → 重命名 → 删除（破坏性放最后）。
        // 等宽，11.5sp 小字；删除用 danger 浅底，其余走中性灰或强调色。
        card.addView(
            ctx.row().apply {
                addView(cardAction("编辑", Tokens.ACCENT, 0x14FE2C55) {
                    shell.openEditor(templateId = group.id)
                }, ctx.lp(0, WRAP, 1f))
                addView(cardAction("预览", Tokens.INK_DIM, Tokens.SEG_BG) {
                    PreviewSheet.open(shell.activity,
                        group.name.ifBlank { "未命名模板" },
                        config.composeProgram(group.body, config.macrosForTemplate(group.id)),
                        group.id,
                    )
                }, ctx.lp(0, WRAP, 1f, left = 5))
                addView(cardAction("克隆", Tokens.INK_DIM, Tokens.SEG_BG) {
                    cloneTo(group)
                }, ctx.lp(0, WRAP, 1f, left = 5))
                addView(cardAction("重命名", Tokens.INK_DIM, Tokens.SEG_BG) {
                    rename(group)
                }, ctx.lp(0, WRAP, 1f, left = 5))
                addView(cardAction("删除", Tokens.DANGER, 0x12E5484D) {
                    remove(group)
                }, ctx.lp(0, WRAP, 1f, left = 5))
            },
            ctx.lp(MATCH, WRAP, top = 8),
        )
        return card
    }

    /** 模板卡底部的等宽小按钮：文字居中、单行，强调/危险色带同色描边。 */
    private fun cardAction(label: String, color: Int, tint: Int, onClick: () -> Unit): TextView =
        ctx.text(label, 11.5f, color, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(ctx.dp(2), ctx.dp(8), ctx.dp(2), ctx.dp(8))
            background = ctx.shape(tint, 10).apply {
                if (tint != Tokens.SEG_BG) setStroke(ctx.dp(1), (color and 0x00FFFFFF) or 0x42000000)
            }
            maxLines = 1
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun createTemplate() {
        shell.openEditor(templateId = "")
    }

    private fun createFrom(name: String, body: String) {
        val id = UUID.randomUUID().toString()
        shell.state.update { config ->
            config.copy(
                groups = config.groups + TemplateGroup(
                    id = id,
                    name = name + " 副本",
                    body = body,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        shell.context.tip("已新建模板")
        shell.rebuild()
    }

    private fun rename(group: TemplateGroup) {
        val input = android.widget.EditText(shell.context).apply { setText(group.name) }
        shell.context.let { c ->
            android.app.AlertDialog.Builder(c)
                .setTitle("重命名模板")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isEmpty()) return@setPositiveButton
                    shell.state.update { config ->
                        config.copy(
                            groups = config.groups.map {
                                if (it.id == group.id) it.copy(name = name, updatedAt = System.currentTimeMillis()) else it
                            },
                        )
                    }
                    shell.rebuild()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun remove(group: TemplateGroup) {
        val members = shell.state.config.membersOf(group.id)
        shell.context.let { c ->
            android.app.AlertDialog.Builder(c)
                .setTitle("删除模板")
                .setMessage(
                    if (members.isEmpty()) "确定删除「${group.name}」？" else
                        "「${group.name}」有 ${members.size} 个会话在继承。删除后这些会话保留各自的文案副本，不会丢内容。",
                )
                .setPositiveButton("删除") { _, _ ->
                    shell.state.update { config ->
                        config.copy(groups = config.groups.filterNot { it.id == group.id })
                    }
                    shell.context.tip("已删除")
                    shell.rebuild()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    /**
     * 克隆到会话：多选。
     *
     * 原来这里是单选的 `pick()`，一个模板想铺给一批人得点十几次；改成勾选列表 +
     * 全选 / 清空，一次写完。已经继承这个模板的会话预勾上，等于「再确认一遍」。
     */
    private fun cloneTo(group: TemplateGroup) {
        val config = shell.state.config
        val targets = config.snapshots.filter { !it.isHidden }
        if (targets.isEmpty()) {
            ctx.tip("还没有会话快照，先进一次抖音")
            return
        }

        val picked = LinkedHashSet<String>()
        val boxes = HashMap<String, android.widget.CheckBox>()
        val body = ctx.column()

        val count = ctx.text("已勾 0 个", Tokens.T_SUB, Tokens.INK_DIM)
        var positive: android.widget.Button? = null
        fun syncCount() {
            count.text = "已勾 ${picked.size} 个 · 本组 ${targets.size} 个"
            // 主按钮跟着勾选数变，0 个时压暗——避免出现「克隆到 0 个会话」这种可点的空操作
            positive?.let {
                it.text = if (picked.isEmpty()) "先勾几个会话" else "克隆到 ${picked.size} 个会话"
                it.alpha = if (picked.isEmpty()) 0.45f else 1f
            }
        }

        val head = ctx.row().apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(count, ctx.lp(WRAP, WRAP, weight = 1f))
        head.addView(
            ctx.text("全选", 12f, Tokens.ACCENT, bold = true).apply {
                setPadding(ctx.dp(8), ctx.dp(4), ctx.dp(8), ctx.dp(4))
                setOnClickListener {
                    targets.forEach { picked.add(it.conversationId) }
                    boxes.values.forEach { it.isChecked = true }
                    syncCount()
                }
            },
        )
        head.addView(
            ctx.text("清空", 12f, Tokens.INK_DIM, bold = true).apply {
                setPadding(ctx.dp(8), ctx.dp(4), ctx.dp(8), ctx.dp(4))
                setOnClickListener {
                    picked.clear()
                    boxes.values.forEach { it.isChecked = false }
                    syncCount()
                }
            },
        )
        body.addView(head, ctx.lp(MATCH, WRAP, left = 14, right = 14, bottom = 4))

        targets.forEach { target ->
            val inherited = config.conversation(target.conversationId)?.inheritFrom == group.id
            val box = android.widget.CheckBox(ctx).apply {
                isChecked = inherited
                setOnCheckedChangeListener { _, checked ->
                    if (checked) picked.add(target.conversationId)
                    else picked.remove(target.conversationId)
                    syncCount()
                }
            }
            if (inherited) picked.add(target.conversationId)
            boxes[target.conversationId] = box
            val line = ctx.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ctx.dp(14), ctx.dp(5), ctx.dp(14), ctx.dp(5))
                addView(box, ctx.lp(WRAP, WRAP))
                addView(
                    ctx.text(target.displayName, 14f, Tokens.INK),
                    ctx.lp(WRAP, WRAP, left = 2, weight = 1f),
                )
                if (inherited) {
                    addView(
                        ctx.text("已继承", 11f, Tokens.INK_DIM),
                        ctx.lp(WRAP, WRAP),
                    )
                }
                setOnClickListener { box.isChecked = !box.isChecked }
            }
            body.addView(line, ctx.lp(MATCH, WRAP))
        }
        syncCount()

        val scroller = android.widget.ScrollView(ctx).apply {
            addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        val dialog = android.app.AlertDialog.Builder(ctx)
            .setTitle("克隆「${group.name}」到…")
            .setView(scroller)
            .setPositiveButton("先勾几个会话", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            positive = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            syncCount()
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (picked.isEmpty()) {
                    ctx.tip("先勾几个会话")
                    return@setOnClickListener
                }
                val stamp = System.currentTimeMillis()
                shell.state.update { current ->
                    var next = current
                    picked.forEach { id ->
                        val old = current.conversation(id)
                        val item = if (old == null) {
                            com.tiantong.spark.app.config.model.ConversationTemplate(
                                conversationId = id,
                                inheritFrom = group.id,
                                body = group.body,
                                parentHashAtSync = group.bodyHash,
                                autoSync = true,
                                updatedAt = stamp,
                            )
                        } else {
                            old.copy(
                                inheritFrom = group.id,
                                body = group.body,
                                parentHashAtSync = group.bodyHash,
                                updatedAt = stamp,
                            )
                        }
                        next = next.copy(
                            conversations = next.conversations.filterNot { it.conversationId == id } + item,
                        )
                    }
                    next
                }
                ctx.tip("已克隆到 ${picked.size} 个会话")
                dialog.dismiss()
                shell.rebuild()
            }
        }
        dialog.show()
    }
}
