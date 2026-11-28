package com.tiantong.spark.app.ui.v2

import com.tiantong.spark.BuildConfig

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.tiantong.spark.app.alarm.DailyAlarm
import com.tiantong.spark.app.provider.ConfigContract
import com.tiantong.spark.app.ui.v2.comp.AppBar
import com.tiantong.spark.app.ui.v2.comp.MATCH
import com.tiantong.spark.app.ui.v2.comp.WRAP
import com.tiantong.spark.app.ui.v2.comp.card
import com.tiantong.spark.app.ui.v2.comp.column
import com.tiantong.spark.app.ui.v2.comp.confirm
import com.tiantong.spark.app.ui.v2.comp.dp
import com.tiantong.spark.app.ui.v2.comp.icon
import com.tiantong.spark.app.ui.v2.comp.lp
import com.tiantong.spark.app.ui.v2.comp.row
import com.tiantong.spark.app.ui.v2.comp.shape
import com.tiantong.spark.app.ui.v2.comp.rounded
import com.tiantong.spark.app.ui.v2.comp.text
import com.tiantong.spark.app.ui.v2.comp.tip
import com.tiantong.spark.app.ui.v2.nav.Page
import com.tiantong.spark.app.ui.v2.nav.PageView
import com.tiantong.spark.app.ui.v2.nav.Shell
import com.tiantong.spark.app.ui.v2.screen.DocsScreen
import com.tiantong.spark.app.ui.v2.screen.HomeScreen
import com.tiantong.spark.app.ui.v2.screen.SettingsScreen
import com.tiantong.spark.app.ui.v2.screen.TemplatesScreen
import com.tiantong.spark.app.ui.v2.sheet.EditorSheet
import com.tiantong.spark.app.ui.v2.state.AppState
import com.tiantong.spark.app.ui.v2.theme.Tokens
import com.tiantong.spark.core.SelfCheck

/**
 * 唯一的 Activity：左边抽屉切页，右边内容区换页。
 *
 * 好处是切页不重建进程状态、返回键就是关抽屉，也就没有「8 个 Activity 各自读一遍配置」
 * 那种一改配置就到处不同步的问题。
 */
class SparkActivity : Activity(), Shell {

    override val activity: Activity get() = this
    override val context: Context get() = this
    override val state: AppState by lazy { AppState(this) }

    private lateinit var appBar: AppBar
    private lateinit var content: FrameLayout
    private lateinit var appBarDivider: View
    private lateinit var drawer: LinearLayout
    private lateinit var shellColumn: LinearLayout
    private lateinit var scrim: View
    private lateinit var fab: View
    private lateinit var selAll: View

    private var current: Page = Page.HOME
    private val pages = mutableMapOf<Page, PageView>()
    private var drawerOpen = false

    private val sendDoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val summary = intent?.getStringExtra(ConfigContract.EXTRA_SUMMARY).orEmpty()
            if (summary.isNotBlank()) {
                Toast.makeText(this@SparkActivity, summary, Toast.LENGTH_LONG).show()
                rebuild()
            }
        }
    }

    private val snapshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            rebuild()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigContract.install(packageName)
        enterEdgeToEdge()
        SelfCheck.runOnce(this)
        setContentView(buildRoot())
        state.reload()
        // 精确闹钟是一次性的，且 force-stop 后 pending alarm 会丢：
        // 每次启动都按当前配置重排一次，hour<0 时 schedule 内部会 cancel。
        runCatching { com.tiantong.spark.app.alarm.VolumeGuard.restore(this) } // 崩溃 / 被杀后兜底还原音量
        DailyAlarm.schedule(this, state.config.alarmHour, state.config.alarmMinute)
        renderDrawer()
        go(Page.HOME)
    }

    override fun onResume() {
        super.onResume()
        register(sendDoneReceiver, ConfigContract.ACTION_SEND_DONE)
        register(snapshotReceiver, ConfigContract.ACTION_SNAPSHOT_UPDATED)
        rebuild()
    }

    override fun onPause() {
        runCatching {
            unregisterReceiver(sendDoneReceiver)
            unregisterReceiver(snapshotReceiver)
        }
        super.onPause()
    }

    @Suppress("DEPRECATION")
    private fun register(receiver: BroadcastReceiver, action: String) {
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= 33) {
            // 宿主跑在抖音进程里，发广播的身份是抖音，所以必须允许接收外部广播。
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    // ── 布局 ──────────────────────────────────────────────────────

    private fun buildRoot(): View {
        val root = FrameLayout(this)
        root.setBackgroundColor(Tokens.BG)

        val shellColumn = column()
        shellColumn.setBackgroundColor(Tokens.BG)
        // edge-to-edge：由 setOnApplyWindowInsetsListener 按真实 insets 补边距（见 applyInsets）
        shellColumn.setPadding(0, 0, 0, 0)
        appBar = AppBar(this, Page.HOME.label, Tokens.Icon.MENU) { toggleDrawer() }
        appBar.action(Tokens.Icon.GEAR, "设置") { go(Page.SETTINGS) }
        shellColumn.addView(appBar, LinearLayout.LayoutParams(MATCH, WRAP))
        appBarDivider = View(this).apply {
            setBackgroundColor(Tokens.LINE)
            alpha = 0f   // 内容没滚动时不显示，滚动后才渐显（见 go 里的滚动监听）
        }
        shellColumn.addView(appBarDivider, LinearLayout.LayoutParams(MATCH, dp(1)))

        content = FrameLayout(this)
        content.setPadding(dp(16), 0, dp(16), 0)
        shellColumn.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(shellColumn, FrameLayout.LayoutParams(MATCH, MATCH))
        this.shellColumn = shellColumn
        applyInsets(root)

        selAll = selectAllPill()
        fab = primaryFab()
        // 「全选」和「发送 N 个」并排：白底红字胶囊在左，红色主按钮在右（间距 10dp）。
        val fabRow = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(selAll)
            addView(fab, lp(WRAP, WRAP, left = 10))
        }
        val fabParams = FrameLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dp(18)
            bottomMargin = dp(24)
        }
        root.addView(fabRow, fabParams)

        scrim = View(this).apply {
            setBackgroundColor(0x6B0C0E14)
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }
        root.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))

        drawer = column().apply {
            setBackgroundColor(Tokens.SURFACE)
            setPadding(dp(12), dp(22), dp(12), dp(16))
            visibility = View.GONE
        }
        val drawerWidth = dp(266)
        root.addView(drawer, FrameLayout.LayoutParams(drawerWidth, MATCH).apply { gravity = Gravity.START })
        root.requestApplyInsets()
        return root
    }

    /**
     * 状态栏/导航条颜色跟页面底色走（深色图标）。
     *
     * 必须真正进入 edge-to-edge：targetSdk 35+（Android 15 起）系统强制
     * `decorFitsSystemWindows=false`，内容会延伸到系统栏下面，不自己吃掉
     * insets 就会出现「状态栏压住标题」的遮挡（见 issue #1）。
     * 这里统一关掉 decor 的自动让位， insets 一律由 [applyInsets] 手动应用，
     * 避免「decor 让了一次 + 我们再让一次」的双倍空带。
     */
    private fun enterEdgeToEdge() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
            @Suppress("DEPRECATION")
            window.statusBarColor = Tokens.BG
            @Suppress("DEPRECATION")
            window.navigationBarColor = Tokens.BG
            // MIUI 上 insetsController 偶发不生效，这里用老接口再钉一遍深色图标
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    /**
     * 手动应用系统栏 insets：内容不再被状态栏/导航条/刘海压住。
     *
     * 返回未消费的 insets 让子 view 继续收到（它们都没有自己的监听，无副作用）。
     * padding 加在 root 上，抽屉/scrim/FAB 全部相对让位后的区域定位，
     * FAB 的 bottomMargin 因此天然落在导航条之上。
     */
    private fun applyInsets(root: View) {
        root.setOnApplyWindowInsetsListener { v, insets ->
            runCatching {
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars =
                        insets.getInsets(
                            android.view.WindowInsets.Type.systemBars() or
                                android.view.WindowInsets.Type.displayCutout(),
                        )
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    v.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom(),
                    )
                }
            }
            insets
        }
    }

    private fun primaryFab(): View =
        row().apply {
            background = roundedPill(Tokens.ACCENT)
            setPadding(dp(18), dp(12), dp(20), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            addView(icon(Tokens.Icon.FIRE, 17f, -1), lp(24, WRAP))
            addView(text("立即发送", Tokens.T_BODY, -1, bold = true), lp(WRAP, WRAP, left = 4))
            visibility = View.GONE
            setOnClickListener { askSend() }
        }

    /** FAB 左侧的「全选/取消全选」：白底红字胶囊，只在主页勾选了行时出现。 */
    private fun selectAllPill(): View =
        text("全选", Tokens.T_BODY, Tokens.ACCENT, bold = true).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = whitePill()
            visibility = View.GONE
            setOnClickListener {
                val groupIds = state.listRows().map { it.conversationId }
                val wasAll = state.groupAllPicked()
                state.togglePickGroup()
                val skip = state.noBodyCount(groupIds)
                when {
                    wasAll -> Unit
                    skip > 0 -> tip("已全选 ${groupIds.size} 个，其中 $skip 个没有文案不会发送", long = true)
                    else -> tip("已全选 ${groupIds.size} 个")
                }
                rebuild()
            }
        }

    private fun whitePill(): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt())
            cornerRadius = dp(26).toFloat()
        }

    private fun roundedPill(color: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(28).toFloat()
        }

    // ── 抽屉 ──────────────────────────────────────────────────────

    private fun renderDrawer() {
        if (!::drawer.isInitialized) return
        drawer.removeAllViews()

        drawer.addView(
            column().apply {
                setPadding(dp(12), dp(10), dp(12), dp(20))
                addView(text("抖音续火花cc", 17f, Tokens.INK, bold = true))
                addView(text(versionLine(), 11.5f, Tokens.INK_DIM), lp(WRAP, WRAP, top = 3))
            },
            LinearLayout.LayoutParams(MATCH, WRAP),
        )
        drawer.addView(View(this).apply { setBackgroundColor(Tokens.LINE) }, LinearLayout.LayoutParams(MATCH, dp(1)))

        Page.main.forEach { page ->
            drawer.addView(drawerRow(page), LinearLayout.LayoutParams(MATCH, WRAP))
        }

        drawer.addView(View(this).apply { setBackgroundColor(Tokens.LINE) }, LinearLayout.LayoutParams(MATCH, dp(1)).apply { topMargin = dp(8) })
        drawer.addView(
            drawerRow(null).apply {
                setOnClickListener {
                    closeDrawer()
                    go(Page.DOCS)
                }
            },
            LinearLayout.LayoutParams(MATCH, WRAP),
        )

        drawer.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))
        drawer.addView(View(this).apply { setBackgroundColor(Tokens.LINE) }, LinearLayout.LayoutParams(MATCH, dp(1)))
        drawer.addView(
            text("Apache-2.0 · 见 LICENSE", 11.5f, Tokens.INK_DIM).apply {
                setPadding(dp(12), dp(12), dp(12), dp(4))
            },
            LinearLayout.LayoutParams(MATCH, WRAP),
        )
    }

    private fun drawerRow(page: Page?): LinearLayout =
        row().apply {
            val active = page != null && page == current
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.CENTER_VERTICAL
            background = shape(if (active) Tokens.ACCENT_SOFT else 0x00000000, 11)
            addView(
                icon(page?.glyph ?: Tokens.Icon.BOOK, 15f, if (active) Tokens.ACCENT else Tokens.INK_DIM),
                lp(20, WRAP).also { it.rightMargin = dp(11) },
            )
            addView(
                text(page?.drawerLabel ?: "使用文档", 14f, if (active) Tokens.ACCENT else Tokens.INK, bold = active),
                lp(0, WRAP, 1f),
            )
            val count = page?.let { countOf(it) } ?: 0
            if (count > 0) {
                addView(text("$count", 11.5f, Tokens.INK_DIM), lp(WRAP, WRAP, left = 6))
            }
            if (page != null) {
                setOnClickListener {
                    go(page)
                }
            }
        }

    private fun countOf(page: Page): Int = when (page) {
        Page.HOME -> state.pending().size
        Page.TEMPLATES -> state.config.groups.size
        else -> 0
    }

    /** 系统栏底色跟随顶部内容（抽屉白底 / 页面灰底），图标保持深色。 */
    private fun tintSystemBars(color: Int) {
        runCatching {
            @Suppress("DEPRECATION") window.statusBarColor = color
            @Suppress("DEPRECATION") window.navigationBarColor = color
        }
    }

    private fun toggleDrawer() {
        if (drawerOpen) closeDrawer() else openDrawer()
    }

    private fun openDrawer() {
        drawerOpen = true
        // 抽屉是白底：状态栏/导航条同色，顶边才不会露出一条浅灰横带
        tintSystemBars(Tokens.SURFACE)
        renderDrawer()
        drawer.visibility = View.VISIBLE
        drawer.translationX = -drawer.width.toFloat()
        scrim.visibility = View.VISIBLE
        drawer.animate().translationX(0f).setDuration(190).start()
        scrim.animate().alpha(1f).setDuration(190).start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        drawer.animate().translationX(-drawer.width.toFloat()).setDuration(160).start()
        scrim.animate().alpha(0f).setDuration(160).withEndAction {
            drawer.visibility = View.GONE
            scrim.visibility = View.GONE
            tintSystemBars(Tokens.BG)
        }.start()
    }

    override fun onBackPressed() {
        if (drawerOpen) {
            closeDrawer()
        } else if (current != Page.HOME) {
            go(Page.HOME)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ── Shell ─────────────────────────────────────────────────────

    override fun go(page: Page) {
        current = page
        appBar.title(page.label)
        content.removeAllViews()
        val view = pages.getOrPut(page) { create(page) }
        val body = view.root()
        // 页面根 View 是复用的：挂到新 ScrollView 前先从旧父节点摘下来
        (body.parent as? android.view.ViewGroup)?.removeView(body)
        val scroller = android.widget.ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(0, 0, 0, dp(96))
            addView(body, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        // 内容一滚动就给顶栏补一条分隔线：不然卡片贴到标题下面像被切成两半。
        scroller.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            appBarDivider.alpha = if (scrollY > dp(2)) 1f else 0f
        }
        content.addView(scroller, FrameLayout.LayoutParams(MATCH, MATCH))
        view.refresh()
        closeDrawer()
        renderDrawer()
        updateFab()
    }

    private fun create(page: Page): PageView = when (page) {
        Page.HOME -> HomeScreen(this)
        Page.TEMPLATES -> TemplatesScreen(this)
        Page.SETTINGS -> SettingsScreen(this)
        Page.DOCS -> DocsScreen(this)
    }

    override fun rebuild() {
        // 改一行归属就整页重建；不记滚动位置的话人会被弹回顶部，等于看不见刚改的那一行。
        val keepY = (content.getChildAt(0) as? android.widget.ScrollView)?.scrollY ?: 0
        state.reload()
        pages[current]?.refresh()
        renderDrawer()
        updateFab()
        if (keepY > 0) {
            val sv = content.getChildAt(0) as? android.widget.ScrollView
            sv?.post { sv.scrollTo(0, keepY) }
        }
    }

    override fun openEditor(conversationId: String?, templateId: String?) {
        EditorSheet.open(this, conversationId = conversationId, templateId = templateId)
    }

    override fun sendNow(ids: List<String>) {
        if (ids.isEmpty()) {
            tip("先勾选要发的会话")
            return
        }
        sendBroadcast(
            Intent(ConfigContract.ACTION_SEND_NOW)
                .putStringArrayListExtra(ConfigContract.EXTRA_TARGETS, ArrayList(ids)),
        )
        tip("已请求发送 ${ids.size} 个，等抖音回结果…", long = true)
    }

    override fun refreshFromHost() {
        sendBroadcast(Intent(ConfigContract.ACTION_REFRESH_SNAPSHOT))
        tip("已请求刷新会话列表")
    }

    override fun say(message: CharSequence) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun askSend() {
        // 只发当前列表里勾上的人：切过分组就自动清空，这里是第二道保险。
        val ids = state.pickedInView()
        if (ids.isEmpty()) return
        val names = ids.mapNotNull { id -> state.config.snapshot(id)?.displayName }
        val preview = if (names.isEmpty()) "" else "\n\n" + names.take(8).joinToString("、") +
            if (names.size > 8) " 等 ${names.size} 个" else ""
        val skip = state.noBodyCount()
        val skipHint = if (skip > 0) "\n\n其中 $skip 个没有文案，发送时会被跳过。" else ""
        confirm(
            "立即发送给 ${ids.size} 个会话？",
            "手动发送不受总开关与「今日已发」限制。$skipHint$preview",
            positive = "发送",
        ) {
            sendNow(ids)
            state.clearPick()
            rebuild()
        }
    }

    private fun updateFab() {
        if (!::fab.isInitialized || !::selAll.isInitialized) return
        val count = state.picked.size
        fab.visibility = if (count > 0) View.VISIBLE else View.GONE
        (fab as? ViewGroup)?.let { group ->
            (group.getChildAt(1) as? android.widget.TextView)?.text = "立即发送 $count 个"
        }
        // 全选入口：主页 + 已有勾选 + 当前分组有会话（含无火花），数量与分组标题一致。
        val showSelAll = count > 0 && current == Page.HOME && state.listRows().isNotEmpty()
        selAll.visibility = if (showSelAll) View.VISIBLE else View.GONE
        if (showSelAll) {
            (selAll as? android.widget.TextView)?.text =
                if (state.groupAllPicked()) "取消全选" else "全选"
        }
    }

    private fun versionLine(): String =
        runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            // 带上安装时间：每装一次都会变，方便确认手机上跑的是不是最新那一包。
            val at = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
                .format(java.util.Date(info.lastUpdateTime))
            "${info.versionName}（${info.versionCode}）· 装于 $at"
        }.getOrDefault("")
}
