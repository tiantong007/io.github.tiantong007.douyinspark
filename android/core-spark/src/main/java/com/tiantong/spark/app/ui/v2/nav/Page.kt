package com.tiantong.spark.app.ui.v2.nav

import android.view.View
import com.tiantong.spark.app.ui.v2.theme.Tokens

/** 主页面。抽屉按这个顺序排列，也决定返回栈的根。 */
enum class Page(val label: String, val glyph: String) {
    HOME("状态看板", Tokens.Icon.FIRE),
    TEMPLATES("模板", Tokens.Icon.TEMPLATE),
    SETTINGS("设置", Tokens.Icon.GEAR),

    /** 使用文档。不在抽屉主列表里，藏在下方的独立入口。 */
    DOCS("使用文档", Tokens.Icon.BOOK),
    ;

    /**
     * 抽屉里显示的名字。
     *
     * 首页在抽屉里叫「状态看板」——抽屉其余几项都是功能名（模板 / 设置 / 使用文档），
     * 只有首页原本写产品名，读起来不齐；产品名只留在抽屉头部（标题栏也统一为「状态看板」）。
     */
    val drawerLabel: String get() = if (this == HOME) "状态看板" else label

    companion object {
        /** 抽屉主列表里的页面（文档页走独立入口，不在这里）。 */
        val main: List<Page> get() = entries.filter { it != DOCS }
    }
}

/**
 * 一个页面。生命周期只有两个：造视图、每次切回来时刷新。
 *
 * 没有 Fragment —— 一个页面就是一个返回 View 的类，切换时整块替换，
 * 状态存在 [com.tiantong.spark.app.ui.v2.state.AppState] 里，页面本身无状态。
 */
interface PageView {
    /** 造好的根视图（同一个实例复用，切页时重新挂载）。 */
    fun root(): View

    /** 重新读数据并刷新视图。 */
    fun refresh()

    /** 页面被切走时调用（停掉定时器之类）。 */
    fun onLeave() {}
}

/** 抽屉里的一项：图标 + 名字 + 右侧计数。 */
data class DrawerItem(
    val page: Page,
    val count: Int = 0,
)
