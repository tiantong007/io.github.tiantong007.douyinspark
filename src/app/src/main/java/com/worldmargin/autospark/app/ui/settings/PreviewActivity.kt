package com.worldmargin.autospark.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.worldmargin.autospark.app.config.ConfigStore
import com.worldmargin.autospark.app.config.StickerCatalog
import com.worldmargin.autospark.app.config.StickerImages
import com.worldmargin.autospark.app.script.PreviewData
import com.worldmargin.autospark.app.script.SparkScriptEngine
import com.worldmargin.autospark.app.ui.common.UiKit.COLOR_ACCENT
import com.worldmargin.autospark.app.ui.common.UiKit.COLOR_DIM
import com.worldmargin.autospark.app.ui.common.UiKit.COLOR_TEXT
import com.worldmargin.autospark.app.ui.common.UiKit.COLOR_WARN
import com.worldmargin.autospark.app.ui.common.UiKit.column
import com.worldmargin.autospark.app.ui.common.UiKit.divider
import com.worldmargin.autospark.app.ui.common.UiKit.dp
import com.worldmargin.autospark.app.ui.common.UiKit.icon
import com.worldmargin.autospark.app.ui.common.UiKit.padSystemBars
import com.worldmargin.autospark.app.ui.common.UiKit.rounded
import com.worldmargin.autospark.app.ui.common.UiKit.row
import com.worldmargin.autospark.app.ui.common.UiKit.scroll
import com.worldmargin.autospark.app.ui.common.UiKit.text
import com.worldmargin.autospark.app.ui.common.UiKit.titleBar
import com.worldmargin.autospark.app.ui.common.UiKit

/**
 * 分段预览：把一段脚本**按条**画出来，看它到底会发出几条消息。
 *
 * 为什么值得单独一页：v2.2.0 起一个脚本能发多段（`ctx.commit()` 切段、贴纸对象插互动表情），
 * 弹窗里拼成一坨文字看不出「哪几句是一条」。这里一条一个气泡，贴纸段单独画成一行。
 *
 * 渲染仍在**模块进程**里做（只算文案、不发送）：调用方把拼好的脚本传进来，
 * 这里补上预览用的变量与沙盒数据，跑完按 [SparkScriptEngine.Rendered.segments] 画。
 */
class PreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val program = intent.getStringExtra(EXTRA_PROGRAM).orEmpty()
        val config = ConfigStore.load(this)
        val sampleIndex = intent.getIntExtra(EXTRA_SAMPLE_INDEX, -1)
        // 传了样例序号就按序号取（设置页整页预览用），否则用第一个样例
        val snapshot = if (sampleIndex >= 0) SparkPreview.sampleAt(config, sampleIndex) else SparkPreview.sample(config)
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: snapshot?.conversationId
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "预览"

        val outcome =
            runCatching {
                SparkScriptEngine.renderSegments(
                    program,
                    SparkPreview.variables(snapshot),
                    PreviewData.env(config, conversationId),
                )
            }

        val body = column().apply { setPadding(dp(16), dp(6), dp(16), dp(16)) }
        outcome
            .onSuccess { rendered ->
                val segments = rendered.segments.ifEmpty { listOf(SparkScriptEngine.SendSegment("text", text = rendered.text)) }
                // 老写法 [STICKER:名] 在引擎里是文本段，真正发送时由宿主侧翻成贴纸，这里跟着翻
                val nonEmpty =
                    segments
                        .map { segment ->
                            if (segment.kind == "text" && StickerCatalog.isStickerMark(segment.text)) {
                                SparkScriptEngine.SendSegment("sticker", name = StickerCatalog.nameOf(segment.text))
                            } else {
                                segment
                            }
                        }.filter { it.kind != "text" || it.text.isNotBlank() }
                if (nonEmpty.isEmpty()) {
                    body.addView(note("脚本没有产出内容（ctx.result 是空的）", COLOR_WARN))
                } else {
                    body.addView(note(if (nonEmpty.size == 1) "会发出 1 条消息" else "会发出 ${nonEmpty.size} 条消息，按下面的顺序", COLOR_DIM))
                    nonEmpty.forEachIndexed { index, segment -> body.addView(bubble(index + 1, segment)) }
                    body.addView(note("发送时贴纸自成一条，不跟文字合并；这里显示的是宿主从抖音缓存里取到的表情图，取不到就只显示名字。", COLOR_DIM))
                }
            }
            .onFailure { body.addView(note("预览失败：" + (it.message ?: it.toString()), COLOR_WARN)) }

        setContentView(
            column().apply {
                setBackgroundColor(UiKit.COLOR_BG)
                padSystemBars()
                addView(titleBar(title) { finish() })
                addView(divider())
                addView(
                    scroll().apply {
                        addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
                )
            },
        )
    }

    /** 一个气泡：文本段靠右蓝色气泡，贴纸段带火焰图标。序号标在气泡上方。 */
    private fun bubble(
        index: Int,
        segment: SparkScriptEngine.SendSegment,
    ): LinearLayout =
        column().apply {
            setPadding(0, dp(10), 0, 0)
            addView(text("第 $index 条", 11f, COLOR_DIM).apply { setPadding(dp(2), 0, 0, dp(4)) })
            addView(
                row().apply {
                    gravity = Gravity.END
                    addView(
                        if (segment.kind == "sticker") {
                            row().apply {
                                background = rounded(0x1A3B7CFF, 14)
                                setPadding(dp(12), dp(10), dp(14), dp(10))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                                val picture = StickerImages.bitmap(context, segment.name)
                                if (picture != null) {
                                    addView(
                                        ImageView(context).apply {
                                            setImageBitmap(picture)
                                            adjustViewBounds = true
                                            scaleType = ImageView.ScaleType.FIT_CENTER
                                            val side = dp(64)
                                            layoutParams = LinearLayout.LayoutParams(side, side).apply { rightMargin = dp(8) }
                                        },
                                    )
                                } else {
                                    // 宿主还没把表情图推过来时退回内置图标
                                    addView(icon(UiKit.Icons.FIRE, 18f, COLOR_WARN).apply { setPadding(0, 0, dp(6), 0) })
                                }
                                addView(stickerLabel(segment.name))
                            }
                        } else {
                            TextView(context).apply {
                                text = segment.text
                                textSize = 15f
                                setTextColor(0xFFFFFFFF.toInt())
                                background = rounded(COLOR_ACCENT, 14)
                                setPadding(dp(14), dp(10), dp(14), dp(10))
                                typeface = Typeface.DEFAULT
                                // 长文本折行：给一个宽度上限，短文本自己收缩
                                maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
                            }
                        },
                    )
                },
            )
        }

    private fun stickerLabel(name: String): TextView =
        text("互动表情：" + name.ifBlank { "（未指定名字）" }, 15f, COLOR_TEXT)

    private fun note(
        content: String,
        color: Int,
    ): TextView =
        text(content, 12f, color).apply {
            setPadding(0, dp(8), 0, dp(4))
        }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PROGRAM = "program"
        private const val EXTRA_CONVERSATION_ID = "conversationId"
        private const val EXTRA_SAMPLE_INDEX = "sampleIndex"

        /**
         * 打开分段预览。
         *
         * @param program 已经拼好的可执行脚本（自定义函数注入 + 正文 + 宏都已合并）
         * @param conversationId 用哪个会话的数据造预览；为空则用第一个样例
         * @param sampleIndex >= 0 时按序号轮换样例（设置页整页预览要每个人不一样）
         */
        fun open(
            context: Context,
            title: String,
            program: String,
            conversationId: String? = null,
            sampleIndex: Int = -1,
        ) {
            context.startActivity(
                Intent(context, PreviewActivity::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_PROGRAM, program)
                    .putExtra(EXTRA_CONVERSATION_ID, conversationId)
                    .putExtra(EXTRA_SAMPLE_INDEX, sampleIndex),
            )
        }
    }
}
