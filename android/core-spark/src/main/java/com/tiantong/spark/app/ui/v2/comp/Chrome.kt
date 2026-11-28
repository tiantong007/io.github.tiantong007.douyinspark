package com.tiantong.spark.app.ui.v2.comp

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.tiantong.spark.app.ui.v2.theme.Tokens

/**
 * 页面外壳与控件：顶栏、按钮、胶囊、状态徽章、空态、提示条。
 * 这些是「看一眼就知道是什么」的东西，不需要业务知识。
 */

/**
 * 顶栏：左侧菜单钮，中间 23sp 大标题，右侧动作钮。
 * 底色跟页面一致（看起来像浮在内容上），按钮是白底小方块。
 */
class AppBar(
    context: Context,
    title: CharSequence,
    glyph: String = Tokens.Icon.MENU,
    onLeading: () -> Unit,
) : LinearLayout(context) {

    private val titleView = context.text(title, 23f, Tokens.INK, bold = true).apply {
        letterSpacing = -0.02f
    }
    private val actions = context.row().apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Tokens.BG)
        setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(14))
        layoutParams = LayoutParams(MATCH, WRAP)

        addView(squareTap(glyph, onLeading), LayoutParams(context.dp(36), context.dp(36)))

        titleView.maxLines = 1
        titleView.ellipsize = android.text.TextUtils.TruncateAt.END
        addView(titleView, LayoutParams(0, WRAP, 1f).also { it.leftMargin = context.dp(12) })

        addView(actions, LayoutParams(WRAP, WRAP))
    }

    fun action(glyph: String, hint: String = "", onClick: () -> Unit): AppBar =
        apply { actions.addView(squareTap(glyph, onClick, hint), LayoutParams(context.dp(36), context.dp(36))) }

    fun title(value: CharSequence): AppBar = apply { titleView.text = value }

    private fun squareTap(glyph: String, onClick: () -> Unit, hint: String = ""): View =
        context.icon(glyph, 16f, Tokens.INK).apply {
            contentDescription = hint
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onClick() }
            rounded(Tokens.SURFACE, Tokens.R_MD)
            elevation = context.dpF(1f)
        }
}

/** 一个 44dp 的方形图标按钮。 */
fun Context.iconTap(
    glyph: String,
    color: Int = Tokens.INK,
    size: Float = 19f,
    hint: String = "",
    onClick: () -> Unit,
): View =
    icon(glyph, size, color).apply {
        contentDescription = hint
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }

/** 主按钮：实心强调色，通栏。 */
fun Context.primaryButton(label: CharSequence, enabled: Boolean = true, onClick: () -> Unit): TextView =
    text(label, 13.5f, 0xFFFFFFFF.toInt(), bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(11), dp(16), dp(11))
        rounded(if (enabled) Tokens.ACCENT else Tokens.MUTED, Tokens.R_MD)
        isEnabled = enabled
        isClickable = enabled
        if (enabled) setOnClickListener { onClick() }
        layoutParams = lp(MATCH, WRAP, top = 12)
    }

/** 次按钮：描边，用在「取消 / 次要动作」。 */
fun Context.ghostButton(label: CharSequence, onClick: () -> Unit): TextView =
    text(label, 13.5f, Tokens.INK).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(11), dp(16), dp(11))
        rounded(Tokens.SEG_BG, Tokens.R_MD)
        isClickable = true
        setOnClickListener { onClick() }
    }

/** 卡片里的动作按钮：比主按钮小一号，灰底，原型 .btn.ghost。 */
fun Context.miniButton(
    label: CharSequence,
    primary: Boolean = false,
    onClick: () -> Unit,
): TextView =
    text(label, 12.5f, if (primary) -1 else Tokens.INK, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(13), dp(8), dp(13), dp(8))
        rounded(if (primary) Tokens.ACCENT else Tokens.SEG_BG, 10)
        isClickable = true
        setOnClickListener { onClick() }
    }

/** 文字动作（无背景），用在卡片底部。 */
fun Context.textAction(label: CharSequence, color: Int = Tokens.ACCENT, onClick: () -> Unit): TextView =
    text(label, 13f, color, bold = true).apply {
        setPadding(dp(8), dp(8), dp(8), dp(8))
        isClickable = true
        setOnClickListener { onClick() }
    }

/** 分段控制器：一排胶囊，选中的填强调色。 */
fun Context.chips(
    labels: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
): LinearLayout =
    row().apply {
        layoutParams = lp(MATCH, WRAP, top = 16, bottom = 12)
        setPadding(dp(3), dp(3), dp(3), dp(3))
        background = shape(Tokens.SEG_BG, Tokens.R_MD)
        labels.forEachIndexed { index, label ->
            val on = index == selected
            addView(
                text(label, 13f, if (on) Tokens.INK else Tokens.INK_DIM, bold = on).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(7), 0, dp(7))
                    if (on) {
                        background = shape(Tokens.SURFACE, 9)
                        elevation = dpF(1.5f)
                    }
                    isClickable = true
                    setOnClickListener { onPick(index) }
                },
                lp(0, WRAP, 1f),
            )
        }
    }

/** 编辑器工具条按钮：小胶囊，图标 + 文字（原型 .tbtn）。 */
fun Context.tbtn(
    glyph: String,
    label: CharSequence,
    fire: Boolean = false,
    onClick: () -> Unit,
): LinearLayout =
    row().apply {
        setPadding(dp(11), dp(7), dp(11), dp(7))
        gravity = Gravity.CENTER_VERTICAL
        background = shape(if (fire) Tokens.ACCENT_SOFT else Tokens.SEG_BG, 9)
        addView(icon(glyph, 12f, if (fire) Tokens.ACCENT else 0xFF4A4E58.toInt()), lp(WRAP, WRAP, right = 5))
        addView(text(label, 12f, if (fire) Tokens.ACCENT else 0xFF4A4E58.toInt(), bold = fire), lp(WRAP, WRAP))
        isClickable = true
        setOnClickListener { onClick() }
    }

/** 状态徽章：小圆点 + 文案，胶囊底。 */
fun Context.badge(label: CharSequence, color: Int, tint: Int): TextView =
    text(label, 10.5f, color, bold = true).apply {
        setPadding(dp(7), dp(2), dp(7), dp(2))
        rounded(tint, 6)
        gravity = Gravity.CENTER
    }

/** 空态：大图标 + 一句主文案 + 一句说明。 */
fun Context.emptyState(glyph: String, title: String, hint: String = ""): LinearLayout =
    column().apply {
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(34), dp(24), dp(34))
        layoutParams = lp(MATCH, WRAP)
        addView(icon(glyph, 34f, Tokens.INK_FAINT), lp(WRAP, WRAP).also { it.gravity = Gravity.CENTER })
        addView(
            text(title, 15f, Tokens.INK_DIM),
            lp(WRAP, WRAP, top = 12).also { it.gravity = Gravity.CENTER },
        )
        if (hint.isNotBlank()) {
            addView(
                text(hint, Tokens.T_SUB, Tokens.INK_FAINT).apply { gravity = Gravity.CENTER },
                lp(MATCH, WRAP, top = 6).also { it.gravity = Gravity.CENTER },
            )
        }
    }

/** 提示条：左侧一条彩色竖线 + 说明文字。 */
fun Context.note(value: CharSequence, color: Int = Tokens.WARN, tint: Int = Tokens.WARN_SOFT): LinearLayout =
    row().apply {
        setPadding(dp(14), dp(11), dp(14), dp(11))
        rounded(tint, 10)
        layoutParams = lp(MATCH, WRAP, top = 12)
        addView(View(context).apply { setBackgroundColor(color) }, lp(3, MATCH).also { it.rightMargin = dp(10) })
        addView(text(value, Tokens.T_SUB, Tokens.INK).apply { setLineSpacing(dpF(3f), 1f) }, lp(0, WRAP, 1f))
    }

/** 分组标题（卡片外的灰色小字）。 */
fun Context.sectionLabel(value: CharSequence): TextView =
    text(value, Tokens.T_SECTION, Tokens.INK_DIM, bold = true).apply {
        setPadding(dp(4), dp(18), dp(4), dp(8))
        layoutParams = lp(MATCH, WRAP)
    }

/** 卡片：白底圆角 + 内边距，子 View 直接 add 进来。 */
fun Context.card(pad: Int = 15): LinearLayout =
    column().apply {
        setPadding(dp(16), dp(pad), dp(16), dp(pad))
        rounded(Tokens.SURFACE, 18)
        layoutParams = lp(MATCH, WRAP, top = 12)
    }

/** 卡片里的一行：左图标 + 主/副两行字 + 右侧附加。 */
fun Context.settingRow(
    glyph: String,
    title: CharSequence,
    subtitle: CharSequence = "",
    value: CharSequence = "",
    color: Int = Tokens.INK,
    glyphColor: Int = Tokens.INK_DIM,
    onClick: (() -> Unit)? = null,
): LinearLayout =
    row().apply {
        setPadding(dp(4), dp(11), dp(4), dp(11))
        layoutParams = lp(MATCH, WRAP)
        if (glyph.isNotBlank()) addView(icon(glyph, 17f, glyphColor), lp(30, WRAP))
        addView(
            column().apply {
                addView(text(title, Tokens.T_BODY, color))
                if (subtitle.isNotBlank()) {
                    addView(text(subtitle, Tokens.T_SUB, Tokens.INK_DIM), lp(MATCH, WRAP, top = 3))
                }
            },
            lp(0, WRAP, 1f),
        )
        if (value.isNotBlank()) addView(text(value, Tokens.T_SUB, Tokens.INK_DIM), lp(WRAP, WRAP).also { it.leftMargin = dp(8) })
        if (onClick != null) {
            addView(icon(Tokens.Icon.CHEVRON, 14f, Tokens.INK_FAINT), lp(WRAP, WRAP).also { it.leftMargin = dp(6) })
            isClickable = true
            setOnClickListener { onClick() }
        }
    }
