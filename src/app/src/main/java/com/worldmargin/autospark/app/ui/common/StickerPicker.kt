package com.worldmargin.autospark.app.ui.common

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.worldmargin.autospark.app.config.StickerCatalog
import com.worldmargin.autospark.app.editor.SparkEditor
import com.worldmargin.autospark.app.ui.common.UiKit.COLOR_DIM
import com.worldmargin.autospark.app.ui.common.UiKit.COLOR_TEXT
import com.worldmargin.autospark.app.ui.common.UiKit.column
import com.worldmargin.autospark.app.ui.common.UiKit.dp
import com.worldmargin.autospark.app.ui.common.UiKit.iconButton
import com.worldmargin.autospark.app.ui.common.UiKit.text
import com.worldmargin.autospark.app.ui.common.UiKit

/**
 * 编辑器里的「插入互动表情」按钮。
 *
 * 为什么要允许手填名字：完整表情目录（21 个）只存在抖音自己的 keva 里，模块进程读不到，
 * 所以这里只列内置的那几个，其余照着抖音表情面板里的名字手打——
 * 发送时宿主进程会拿抖音当前的目录去匹配（见 host/im/StickerCatalog）。
 */
fun Activity.stickerButton(editor: SparkEditor?): View =
    iconButton(UiKit.Icons.FIRE, "", compact = true, hint = "插入互动表情") {
        if (editor == null) return@iconButton
        showStickerPicker { name -> insertSticker(editor, name) }
    }

/** 弹窗：内置表情直接点，最后一项走手填。 */
private fun Activity.showStickerPicker(onPick: (String) -> Unit) {
    val builtIn = StickerCatalog.names()
    val labels = builtIn + listOf("手动输入名字…")
    AlertDialog
        .Builder(this)
        .setTitle("插入互动表情")
        .setItems(labels.toTypedArray()) { _, index ->
            if (index < builtIn.size) onPick(builtIn[index]) else askStickerName(onPick)
        }.setNegativeButton("取消", null)
        .show()
}

/** 手填名字：只留文字与数字，避免把引号带进脚本。 */
private fun Activity.askStickerName(onPick: (String) -> Unit) {
    val input =
        EditText(this).apply {
            hint = "抖音里显示的名字，例如 比心"
            textSize = 15f
            setTextColor(COLOR_TEXT)
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
    val box =
        column().apply {
            setPadding(dp(20), dp(4), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(
                text("名字要和抖音表情面板里的一致，否则发送时匹配不到那张图。", 12f, COLOR_DIM)
                    .apply { setPadding(0, dp(8), 0, dp(4)) },
            )
        }
    AlertDialog
        .Builder(this)
        .setTitle("互动表情名字")
        .setView(box)
        .setPositiveButton("插入") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isBlank()) {
                Toast.makeText(this, "名字不能为空", Toast.LENGTH_SHORT).show()
            } else {
                onPick(name)
            }
        }.setNegativeButton("取消", null)
        .show()
}

/** 把一段贴纸 push 插到光标处。 */
private fun Activity.insertSticker(
    editor: SparkEditor,
    name: String,
) {
    editor.insert("ctx.result.push({ type: \"sticker\", name: \"$name\" });")
    Toast.makeText(this, "已插入互动表情「$name」", Toast.LENGTH_SHORT).show()
}
