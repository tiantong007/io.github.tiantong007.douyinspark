package com.tiantong.spark.app.ui.v2.sheet

import android.app.Activity
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.tiantong.spark.app.config.ConfigStore
import com.tiantong.spark.app.config.EmojiCatalog
import com.tiantong.spark.app.config.EmojiImages
import com.tiantong.spark.app.script.PreviewData
import com.tiantong.spark.app.script.SparkScriptEngine
import com.tiantong.spark.app.script.SparkPreview
import com.tiantong.spark.app.ui.v2.comp.MATCH
import com.tiantong.spark.app.ui.v2.comp.WRAP
import com.tiantong.spark.app.ui.v2.comp.Sheet
import com.tiantong.spark.app.ui.v2.comp.bubble
import com.tiantong.spark.app.ui.v2.comp.column
import com.tiantong.spark.app.ui.v2.comp.divider
import com.tiantong.spark.app.ui.v2.comp.formHint
import com.tiantong.spark.app.ui.v2.comp.icon
import com.tiantong.spark.app.ui.v2.comp.lp
import com.tiantong.spark.app.ui.v2.comp.mono
import com.tiantong.spark.app.ui.v2.comp.row
import com.tiantong.spark.app.ui.v2.comp.text
import com.tiantong.spark.app.ui.v2.theme.Tokens
import com.tiantong.spark.app.ui.v2.comp.dp
import com.tiantong.spark.app.ui.v2.comp.dpF
import com.tiantong.spark.app.ui.v2.comp.shape

/**
 * 按条预览：把脚本跑一遍，看看实际会发出几条消息、每条长什么样。
 *
 * 用真实引擎渲染，所以变量、宏、分段、表情的写法和发送时完全一致；
 * 只是不真的发出去（沙箱标记 preview=true，写操作与回调都被挡住）。
 */
object PreviewSheet {

    fun open(
        activity: Activity,
        title: String,
        program: String,
        conversationId: String?,
        sampleIndex: Int = -1,
    ) {
        val config = ConfigStore.load(activity)
        val snapshot = if (sampleIndex >= 0) {
            SparkPreview.sampleAt(config, sampleIndex)
        } else if (conversationId != null) {
            config.snapshot(conversationId) ?: SparkPreview.sample(config)
        } else {
            SparkPreview.sample(config)
        }

        Sheet.open(activity, title, snapshot?.displayName ?: "占位样例") { body ->
            build(activity, body, program, conversationId, sampleIndex, top = 10)
        }
    }

    /**
     * 内联预览：把预览块直接铺进已有的容器（编辑弹层里用），不另开一层弹窗。
     * 返回的 View 可以留着，下次点预览先移除它再重画。
     */
    fun inline(
        context: android.content.Context,
        container: LinearLayout,
        program: String,
        conversationId: String?,
    ): View {
        val block = context.column().apply { layoutParams = context.lp(MATCH, WRAP) }
        block.addView(context.divider(Tokens.LINE, 1), context.lp(MATCH, WRAP, top = 14, bottom = 2))
        build(context, block, program, conversationId, -1, top = 12)
        container.addView(block, context.lp(MATCH, WRAP))
        return block
    }

    private fun build(
        context: android.content.Context,
        body: LinearLayout,
        program: String,
        conversationId: String?,
        sampleIndex: Int,
        top: Int,
    ) {
        val config = ConfigStore.load(context)
        val snapshot = if (sampleIndex >= 0) {
            SparkPreview.sampleAt(config, sampleIndex)
        } else if (conversationId != null) {
            config.snapshot(conversationId) ?: SparkPreview.sample(config)
        } else {
            SparkPreview.sample(config)
        }
        val variables = SparkPreview.variables(snapshot)
        val env = PreviewData.env(config, conversationId)

        val rendered = runCatching {
            SparkScriptEngine.renderSegments(program, variables, env)
        }
        rendered.onFailure { error ->
            body.addView(context.text("预览失败", Tokens.T_BODY, Tokens.DANGER, bold = true), context.lp(MATCH, WRAP, top = top))
            body.addView(context.mono(error.message ?: error.toString(), 12f, Tokens.INK_DIM), context.lp(MATCH, WRAP, top = 6))
            return
        }

        val result = rendered.getOrNull() ?: return
        val segments = segmentsOf(result)

        body.addView(
            context.text(headline(segments.size), 14f, Tokens.INK, bold = true),
            context.lp(MATCH, WRAP, top = top),
        )
        if (segments.isEmpty()) {
            body.addView(
                context.text("脚本没有产出内容（ctx.result 是空的）", Tokens.T_SUB, Tokens.INK_DIM),
                context.lp(MATCH, WRAP, top = 8),
            )
            return
        }

        segments.forEachIndexed { index, segment ->
            body.addView(
                context.text("第 ${index + 1} 条", 10.5f, Tokens.INK_FAINT),
                context.lp(WRAP, WRAP, top = 14).also { it.gravity = Gravity.END },
            )
            body.addView(
                context.row().apply {
                    gravity = Gravity.END
                    addView(bubbleOf(context, segment), context.lp(WRAP, WRAP))
                },
                context.lp(MATCH, WRAP, top = 5),
            )
        }

        body.addView(
            context.formHint("表情直接以贴图显示（缓存里的真实表情图，取不到用内置贴图兜底）。发送时表情自成一条，不跟文字合并。"),
        )
    }

    private fun segmentsOf(result: SparkScriptEngine.Rendered): List<SparkScriptEngine.SendSegment> {
        val raw = result.segments.ifEmpty {
            listOf(SparkScriptEngine.SendSegment("text", text = result.text))
        }
        return raw.filter { it.kind != "text" || it.text.isNotBlank() }
    }

    private fun headline(count: Int): String =
        if (count <= 1) "会发出 1 条消息" else "会发出 $count 条消息，按下面的顺序"

    private fun bubbleOf(context: android.content.Context, segment: SparkScriptEngine.SendSegment): View {
        val limit = (context.resources.displayMetrics.widthPixels * 0.82f).toInt()
        return if (segment.kind == "emoji") {
            context.column().apply {
                gravity = Gravity.CENTER
                background = context.shape(Tokens.SURFACE_SUNK, Tokens.R_LG)
                setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(8))
                val bitmap: Bitmap? = runCatching { EmojiImages.bitmap(context, segment.name) }.getOrNull()
                if (bitmap != null) {
                    addView(
                        ImageView(context).apply {
                            setImageBitmap(bitmap)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        },
                        context.lp(78, 78),
                    )
                } else {
                    addView(
                        context.icon(Tokens.Icon.FIRE, 34f, Tokens.ACCENT),
                        context.lp(78, 78).also { it.gravity = Gravity.CENTER },
                    )
                }
            }
        } else {
            context.text(segment.text, 13f, -1).apply {
                bubble(Tokens.ACCENT, 14, 4)
                setPadding(context.dp(12), context.dp(9), context.dp(12), context.dp(9))
                this.maxWidth = if (limit > 0) limit else 600
                setLineSpacing(context.dpF(3f), 1f)
            }
        }
    }

}
