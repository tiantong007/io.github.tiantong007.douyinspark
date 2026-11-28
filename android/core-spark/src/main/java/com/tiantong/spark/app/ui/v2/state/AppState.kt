package com.tiantong.spark.app.ui.v2.state

import android.content.Context
import com.tiantong.spark.app.config.ConfigStore
import com.tiantong.spark.app.config.SparkConfig
import com.tiantong.spark.app.config.model.ConversationSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 页面共享的状态：一份配置 + 三个界面偏好（过滤、搜索词、勾选）。
 *
 * 只有这里持有可变字段；页面每次刷新都从这里取快照，算完就丢，
 * 不做双向绑定，也不缓存 View。数据量是几十条会话，现算足够快。
 */
class AppState(context: Context) {

    private val app = context.applicationContext
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 界面偏好（分段位置）不进 SparkConfig：那是可导出可分享的配置，视图状态不该跟着跑。 */
    private val prefs = app.getSharedPreferences("spark_ui", Context.MODE_PRIVATE)

    var config: SparkConfig = SparkConfig()
        private set

    /**
     * 首页分段：0 全部 / 1 已燃 / 2 待燃 / 3 待续 / 4 无火花。
     *
     * 「已燃」= 今天有互动、火花已经点燃；「待燃」= 今天发过了但对方还没回，等他回就燃；
     * 「待续」= 今天还没发、抖音说该续；「无火花」= 两边根本没有火花，发也燃不起来。
     */
    private var _filter: Int = prefs.getInt("filter", 0).coerceIn(0, 4)

    var filter: Int
        get() = _filter
        set(value) {
            val next = value.coerceIn(0, 4)
            if (next == _filter) return
            _filter = next
            prefs.edit().putInt("filter", next).apply()
            // 列表换了，勾选必须跟着失效：在哪一组勾的全选，只在那一组里生效。
            picked.clear()
        }

    /**
     * 首页第二行分段（发送名单）：-1 不生效 / 0 自动发送 / 1 不发送。
     *
     * 与 [filter] 互斥 —— 任何一行被点选，另一行的选中态就清空，
     * 这样「列表里这些人是谁筛出来的」永远只有一个答案。
     */
    private var _mstat: Int = prefs.getInt("mstat", -1).coerceIn(-1, 1)

    var mstat: Int
        get() = _mstat
        set(value) {
            val next = value.coerceIn(-1, 1)
            if (next == _mstat) return
            _mstat = next
            prefs.edit().putInt("mstat", next).apply()
            picked.clear()
        }

    var query: String = ""
        set(value) {
            val next = value.trim()
            if (next == field) return
            field = next
            picked.clear()
        }

    /** 刚刚被改过归属的会话 id：重建列表时把那一行标一下，否则人一移走就找不到。 */
    var touchedId: String? = null

    /** 首页列表的「群聊 / 私聊」折叠状态：只影响视觉，不影响勾选与全选。 */
    var gsecGroup: Boolean = false
    var gsecPerson: Boolean = false

    /** 首页勾选的会话 id（跨刷新保留，已消失的自动剔除）。 */
    val picked: MutableSet<String> = linkedSetOf()

    fun reload() {
        config = ConfigStore.load(app)
        val alive = config.snapshots.map { it.conversationId }.toSet()
        picked.retainAll { it in alive }
    }

    fun save(next: SparkConfig) {
        runCatching { ConfigStore.save(app, next) }
        config = next
    }

    /** 读改写；失败（配置损坏）时保持原状并返回 false。 */
    fun update(block: (SparkConfig) -> SparkConfig): Boolean {
        val next = runCatching { ConfigStore.update(app, block) }.getOrNull() ?: return false
        config = next
        return true
    }

    // ── 派生视图 ────────────────────────────────────────────────

    fun today(): String = dayFormat.format(Date())

    fun sentToday(id: String): Boolean = config.sentDays[id] == today()

    /** 可见会话（去掉连续多次没再出现的）。 */
    fun visible(): List<ConversationSnapshot> = config.snapshots.filter { !it.isHidden }

    /** 该续的会话：今天已经发过的 + 现在抖音还说该续的。火花天数多的排前面。 */
    fun renewable(): List<ConversationSnapshot> =
        sortedRows(visible().filter { sentToday(it.conversationId) || needsRenew(it) })

    /**
     * 该续但今天还没发 —— 「待续」页签与发送按钮的作用对象。
     *
     * 注意：发完一条之后抖音会把 state 改成 1（已燃），needsRenewal 立刻变 false。
     * 如果这里只看 needsRenewal，已发的会话会从分母里掉出去，
     * 出现「明明发了 16 个，界面只显示 5/5」这种对不上的情况。
     * 所以分母用 [renewable]（并上今天已发），分子用 [sentList]。
     */
    fun pending(): List<ConversationSnapshot> =
        renewable().filter { !sentToday(it.conversationId) && needsRenew(it) }

    /** 三态优先级已经写在 [SparkConfig.needsRenewal] 里（固定发送 > 固定不发 > 抖音判定）。 */
    fun needsRenew(snapshot: ConversationSnapshot): Boolean = config.needsRenewal(snapshot)

    /** 该续且今天已经发过。 */
    fun sentList(): List<ConversationSnapshot> =
        renewable().filter { sentToday(it.conversationId) }

    // ── 首页用的四个计数 ──────────────────────────────────────────

    /** 今天已发：只要今天发过就算，不再要求「现在还需要续」。 */
    fun doneCount(): Int = visible().count { sentToday(it.conversationId) }

    /** 已经点燃：抖音说 state=1。 */
    fun litCount(): Int = visible().count { it.state == 1 }

    /** 发过了、等对方回（state 2 待对方回 / 3 可重燃）。 */
    fun waitingCount(): Int = visible().count { sentToday(it.conversationId) && (it.state == 2 || it.state == 3) }

    /** 根本没有火花。 */
    fun deadCount(): Int = visible().count { !it.hasSpark }

    /** 有火花但今天还没续。 */
    fun todoCount(): Int = visible().count { it.hasSpark && needsRenew(it) && !sentToday(it.conversationId) }

    /** 联动模板的会话数（去重）—— hero 两个比值的分母。 */
    fun templateCount(): Int =
        config.conversations.count { (it.inheritFrom ?: SparkConfig.BUILTIN_BLANK) != SparkConfig.BUILTIN_BLANK }

    /** 最近一次抓到快照的时间（首页「记录时间」）。 */
    fun recordedAt(): Long =
        config.snapshots.maxOfOrNull { it.updatedAt }?.takeIf { it > 0 } ?: config.hostNoteAt

    /** 抖音进程是否在线：最近 5 分钟内有回话（hostAliveAt）。 */
    fun hostOnline(): Boolean =
        config.hostAliveAt > 0 && System.currentTimeMillis() - config.hostAliveAt <= 5 * 60 * 1000L

    /** 今天进度：已发 / 该续。 */
    fun progress(): Pair<Int, Int> = sentList().size to renewable().size

    /** 当前过滤 + 搜索词下的列表。 */
    fun shown(): List<ConversationSnapshot> {
        val base = when (filter) {
            1 -> visible().filter { it.state == 1 }
            2 -> visible().filter { sentToday(it.conversationId) && (it.state == 2 || it.state == 3) }
            3 -> visible().filter { needsRenew(it) && !sentToday(it.conversationId) }
            4 -> visible().filter { !it.hasSpark }
            else -> visible()
        }
        val key = query.trim()
        val filtered =
            if (key.isEmpty()) base
            else base.filter {
                it.displayName.contains(key, ignoreCase = true) ||
                    it.conversationId.contains(key, ignoreCase = true)
            }
        return sortedRows(filtered)
    }

    /** 勾选且仍在当前列表里的会话 —— 首页「发送」按钮的作用对象。 */
    fun pickedInView(): List<String> {
        val view = listRows().mapTo(HashSet()) { it.conversationId }
        return picked.filter { it in view }
    }

    fun isPicked(id: String): Boolean = id in picked

    fun togglePick(id: String) {
        if (!picked.add(id)) picked.remove(id)
    }

    /**
     * 当前列表（[listRows]）内会话是否已全部勾选 —— 「全选/取消全选」按钮文案依据。
     *
     * 注意口径必须是 [listRows] 而不是 [shown]：第二行分段（自动发送 / 不发送）生效时，
     * 屏幕上看到的是名单，全选也该选名单，否则按钮提示的数量和实际选中的不是同一批人。
     */
    fun groupAllPicked(): Boolean {
        val sel = listRows()
        return sel.isNotEmpty() && sel.all { it.conversationId in picked }
    }

    /** 全选/取消全选当前分组全部会话（含无火花），数量与分组标题一致。 */
    fun togglePickGroup() {
        val sel = listRows()
        if (sel.isEmpty()) return
        if (sel.all { it.conversationId in picked }) {
            sel.forEach { picked.remove(it.conversationId) }
        } else {
            picked.addAll(sel.map { it.conversationId })
        }
    }

    /**
     * 勾选里「没有文案」的会话数：这些发送时会被跳过。
     *
     * 口径与宿主 scriptFor 一致：脚本体为空且没挂宏 = 没有文案。
     */
    fun noBodyCount(ids: Collection<String> = picked): Int = ids.count { id ->
        val own = config.conversation(id)
        val body = own?.body?.trim().orEmpty()
        val macros = config.macrosFor(listOf(id) + listOfNotNull(own?.inheritFrom))
        body.isEmpty() && macros.isEmpty()
    }

    fun clearPick() {
        picked.clear()
    }

    // ── 发送名单（三种归属） ──────────────────────────────────────

    /** 某个会话在「自动发送名单」里的归属。 */
    fun membershipOf(id: String): Membership = when {
        id in config.forcedRenew -> Membership.RENEW
        id in config.forcedSkip -> Membership.SKIP
        else -> Membership.AUTO
    }

    /** 改归属：三态互斥写入，[Membership.AUTO] 两组名单都清掉。 */
    fun setMembership(id: String, mode: Membership) {
        update { c ->
            val renew = if (mode == Membership.RENEW) c.forcedRenew + id else c.forcedRenew - id
            val skip = if (mode == Membership.SKIP) c.forcedSkip + id else c.forcedSkip - id
            c.copy(forcedRenew = renew, forcedSkip = skip)
        }
    }

    /**
     * 会不会进「自动发送名单」。
     *
     * 口径与主页、与 [SparkConfig.needsRenewal] 分层：这里回答的是「只要今天还没发、会不会自动续」，
     * 所以**有火花就算进**（已燃只是今天续过了，明天照样续）；固定不发永远不进，固定发送没火花也进。
     */
    fun inAutoList(snapshot: ConversationSnapshot): Boolean =
        when (membershipOf(snapshot.conversationId)) {
            Membership.SKIP -> false
            Membership.RENEW -> true
            Membership.AUTO -> snapshot.hasSpark
        }

    fun autoRows(): List<ConversationSnapshot> = visible().filter { inAutoList(it) }

    fun noSendRows(): List<ConversationSnapshot> = visible().filter { !inAutoList(it) }

    /** 名单人数（hero 的「自动」与大分母）。 */
    fun autoCount(): Int = autoRows().size

    fun noSendCount(): Int = noSendRows().size

    /** 手动固定发送的人数（hero 小字「固定发送 N」）。 */
    fun fixedSendCount(): Int = autoRows().count { membershipOf(it.conversationId) == Membership.RENEW }

    /** 这个会话有没有文案（没写脚本、也没挂宏 = 没有）。 */
    fun hasScript(id: String): Boolean = noBodyCount(listOf(id)) == 0

    /** 该发（今天待续）却还没写文案 —— 会被跳过，列表里置顶提醒。 */
    fun noTextNeeded(snapshot: ConversationSnapshot): Boolean =
        !hasScript(snapshot.conversationId) && needsRenew(snapshot) && !sentToday(snapshot.conversationId)

    /**
     * 唯一比较器：无文案(0) → 有火花(1) → 无火花(2)，同级按火花天数降序，再按名字。
     * 首页、名单、待续/已发等所有列表共用同一口径，保证同一个人在哪都排在同一个位置。
     * 权重先整体算一遍，避免比较器里反复 hasScript → macrosFor 带来卡顿。
     */
    fun sortedRows(rows: List<ConversationSnapshot>): List<ConversationSnapshot> {
        if (rows.size <= 1) return rows
        val weight = HashMap<String, Int>(rows.size * 2)
        rows.forEach { snapshot ->
            val id = snapshot.conversationId
            weight[id] = when {
                noTextNeeded(snapshot) -> 0
                snapshot.hasSpark -> 1
                else -> 2
            }
        }
        return rows.sortedWith(
            compareBy(
                { weight[it.conversationId] ?: 2 },
                { -it.days },
                { it.displayName },
            ),
        )
    }

    /**
     * 第二行分段生效时列表里的人。
     *
     * 排序：没有文案的排最前（先让你看见谁会掉队）→ 手动指定过的其次 → 其余按火花天数从多到少。
     */
    fun memberRows(): List<ConversationSnapshot> {
        val base = if (mstat == 0) autoRows() else noSendRows()
        val key = query.trim()
        val filtered =
            if (key.isEmpty()) base
            else base.filter {
                it.displayName.contains(key, ignoreCase = true) ||
                    it.conversationId.contains(key, ignoreCase = true)
            }
        // 统一比较器：无文案 → 有火花(天数降序) → 无火花(天数降序)，与看板/其它列表同一口径。
        return sortedRows(filtered)
    }

    /** 当前该渲染的列表：第二行分段优先，否则走五档 + 搜索词。 */
    fun listRows(): List<ConversationSnapshot> = if (mstat >= 0) memberRows() else shown()

    /**
     * 名单里指向「已经不在会话列表里的人」：删好友、换设备、会话 id 变了之后留下的残留。
     *
     * 不自动清 —— 清掉就等于用户设过的「固定发送」无声消失。这些 id 在名单底部以
     * 「已失联」灰行显示，用户自己点「恢复默认」才真正删掉。
     */
    fun ghostRows(): List<Pair<String, Membership>> {
        val alive = config.snapshots.map { it.conversationId }.toSet()
        val out = mutableListOf<Pair<String, Membership>>()
        config.forcedRenew.filter { it !in alive }.forEach { out += it to Membership.RENEW }
        config.forcedSkip.filter { it !in alive }.forEach { out += it to Membership.SKIP }
        return out
    }

    /** 当前名单档位下该显示的失联条目。 */
    fun ghostsFor(mstat: Int): List<Pair<String, Membership>> = when (mstat) {
        0 -> ghostRows().filter { it.second == Membership.RENEW }
        1 -> ghostRows().filter { it.second == Membership.SKIP }
        else -> emptyList()
    }
}

/** 会话在自动发送名单里的归属：默认（跟抖音判定）/ 固定发送 / 固定不发。 */
enum class Membership(val label: String) {
    AUTO("默认"),
    RENEW("固定发送"),
    SKIP("固定不发"),
}
