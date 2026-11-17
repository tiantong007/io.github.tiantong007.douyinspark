package com.worldmargin.autospark.app.editor

import android.app.Activity
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import com.worldmargin.autospark.app.config.SparkConfig
import com.worldmargin.autospark.app.config.model.TemplateVar

/**
 * 脚本编辑器接口。
 *
 * cc 版已移除 WebView + CodeMirror（省 ~1.2 MB），只保留原生 EditText 实现。
 * 接口保留是为了不动 5 个调用方（模板 / 会话 / 宏 / 函数 / 变量编辑页）。
 */
interface SparkEditor {
    /** 放进界面的 view。 */
    val view: View

    /** 当前文本。 */
    val text: String

    fun setText(value: String)

    /** 类型注入：原生编辑器无补全，空实现。 */
    fun applyTypes(
        config: SparkConfig,
        templateVars: List<TemplateVar> = emptyList(),
    ) {}

    /** 在当前光标 / 选区处插入文本（「粘贴」按钮用）。 */
    fun insert(text: String)

    /** 快速格式化：清不可见字符 / 智能引号 / Markdown 围栏。 */
    fun format()

    /** 紧凑模式：小输入框用（收字号与内边距）。 */
    fun setCompact(compact: Boolean) {}

    /** 观察内容变化（给紧凑模式算高度用）。 */
    fun observeTextChange(onChange: (String) -> Unit) {}

    /** 页面销毁时释放。 */
    fun release() {}
}

/** 编辑器工厂。 */
object SparkEditors {
    fun create(
        activity: Activity,
        @Suppress("UNUSED_PARAMETER") config: SparkConfig,
    ): SparkEditor = PlainSparkEditor(activity)
}

/**
 * 原生 EditText 编辑器（替代原来的 WebView + CodeMirror）。
 *
 * 等宽字体、多行、关自动纠错；`format()` 做基础清理，没有语法高亮和补全。
 */
class PlainSparkEditor(
    private val activity: Activity,
) : SparkEditor {
    private val container = FrameLayout(activity)
    private val edit =
        EditText(activity).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.TOP or Gravity.START
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setHorizontallyScrolling(false)
            isVerticalScrollBarEnabled = true
            background = null
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
    private var onChange: ((String) -> Unit)? = null

    init {
        container.addView(
            edit,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        edit.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    onChange?.invoke(s?.toString() ?: "")
                }
            },
        )
    }

    override val view: View get() = container

    override val text: String get() = edit.text?.toString() ?: ""

    override fun setText(value: String) {
        edit.setText(value)
        edit.setSelection(edit.text?.length ?: 0)
    }

    override fun insert(text: String) {
        val start = edit.selectionStart.coerceAtLeast(0)
        val end = edit.selectionEnd.coerceAtLeast(0)
        edit.text?.replace(minOf(start, end), maxOf(start, end), text)
    }

    override fun format() {
        val cleaned =
            text
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "")
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
        val noFence = Regex("^```[a-zA-Z]*\\s*$", RegexOption.MULTILINE).replace(cleaned, "")
        setText(noFence.trim())
    }

    override fun setCompact(compact: Boolean) {
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 13f)
        val h = dp(if (compact) 8 else 12)
        val v = dp(if (compact) 4 else 10)
        edit.setPadding(h, v, h, v)
    }

    override fun observeTextChange(onChange: (String) -> Unit) {
        this.onChange = onChange
    }

    override fun release() {
        container.removeAllViews()
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            activity.resources.displayMetrics,
        ).toInt()
}
