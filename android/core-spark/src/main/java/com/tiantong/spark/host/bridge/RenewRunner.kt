package com.tiantong.spark.host.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import com.tiantong.spark.core.HostRuntime
import com.tiantong.spark.core.ModLog
import com.tiantong.spark.host.account.AccountBridge
import com.tiantong.spark.app.config.BuiltinTemplates
import com.tiantong.spark.app.config.model.ConversationSnapshot
import com.tiantong.spark.app.config.SparkConfig
import com.tiantong.spark.app.config.ConfigClient
import com.tiantong.spark.host.im.AvatarCache
import com.tiantong.spark.host.im.EmojiImageCache
import com.tiantong.spark.app.provider.ConfigContract
import com.tiantong.spark.app.script.EmptyResultException
import com.tiantong.spark.app.script.SparkScriptEngine
import com.tiantong.spark.host.im.ImBridge
import com.tiantong.spark.app.config.EmojiCatalog
import com.tiantong.spark.host.im.EmojiCatalog as HostEmojiCatalog
import com.tiantong.spark.host.im.ImMsgBridge
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.Function
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import com.tiantong.spark.app.config.model.SandboxVar
import com.tiantong.spark.app.script.TemplateVarPrelude
import com.tiantong.spark.core.DouToastHelper

/**
 * 自动续火花（运行在抖音进程内）。
 *
 * 流程：读配置 → 扫描会话 → 取「需要续」的 → 用模板脚本渲染文案 → 发送 → 记录当日已发。
 * 文案来源：该会话的模板内容；无则用默认模板；再无则用 [DEFAULT_SCRIPT]。
 */

internal fun sendNowAndNotify(
    context: Context,
    targets: List<String>,
    toast: Boolean = false,
) {
    wakeModule(context)
    val summary = runRenew(context, respectEnabled = false, only = targets.toSet(), ignoreSentToday = true)
    // 发完把快照写回模块 App：不用打开 App 也能看到最新状态与已发记录
    pushSnapshotToModule(context, attempts = 2)
    val line =
        if (summary.optBoolean("ok")) {
            val sent = summary.optJSONArray("sent")
            val skipped = summary.optJSONArray("skipped")
            val failed = summary.optJSONArray("failed")
            "手动发送：已发 ${sent?.length() ?: 0} / 跳过 ${skipped?.length() ?: 0} / 失败 ${failed?.length() ?: 0}" +
                detailOf(sent, "已发") + detailOf(skipped, "跳过") + detailOf(failed, "失败")
        } else {
            "手动发送未执行：${summary.optString("error")}"
        }
    HostTrace.add(context, line)
    ModLog.i(line)
    if (toast) {
        val brief = line.lineSequence().first()
        runCatching { Toast.makeText(context, brief, Toast.LENGTH_LONG).show() }
    }
    runCatching {
        context.sendBroadcast(
            Intent(ConfigContract.ACTION_SEND_DONE).putExtra(ConfigContract.EXTRA_SUMMARY, line),
        )
    }.onFailure { ModLog.e("发送结果广播失败", it) }
}

/** 抖音设置页入口里的「立即续火花」：就地发一轮，结果用提示告诉用户。 */

fun manualSendInPlace(context: Context) {
    Thread({ sendNowAndNotify(context, emptyList(), toast = true) }, "spark-send-now")
        .apply { isDaemon = true }
        .start()
}

/** 把结果数组拼成「名字（原因）」的短串，便于界面直接展示。 */

internal fun detailOf(
    items: JSONArray?,
    label: String,
): String {
    if (items == null || items.length() == 0) return ""
    val names =
        (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").ifBlank { item.optString("conversationId").takeLast(6) }
            val reason = item.optString("reason").ifBlank { item.optString("error") }
            if (reason.isBlank()) name else "$name（$reason）"
        }
    return if (names.isEmpty()) "" else "\n$label：" + names.joinToString("、")
}

/**
 * 同一时刻只允许一轮发送。抢不到就排队轮询，超过 [WAIT_FOR_PREVIOUS_MS] 才放弃 ——
 * 手动发送撞上定时任务时，排队的体验比直接报错好。
 */
internal val renewRunning = java.util.concurrent.atomic.AtomicBoolean(false)

/** 抢不到发送锁时最多排队等这么久（正常一轮几十秒，留足余量）。 */

private const val WAIT_FOR_PREVIOUS_MS = 120_000L

/** 排队轮询间隔。 */

private const val QUEUE_POLL_MS = 200L

/**
 * 跑一轮续火花。
 *
 * [respectEnabled] 为 false 时无视总开关（手动发送），[only] 非空时只处理这些会话。
 */
fun runRenew(
    context: Context,
    respectEnabled: Boolean,
    only: Set<String> = emptySet(),
    ignoreSentToday: Boolean = false,
    repeatCount: Int = 1,
): JSONObject {
    // 同一时刻只允许一轮：否则「启动补跑 + 前台补跑」会各发一次。
    // 抢不到时不直接报错——手动发送撞上自动任务时，用户只会看到「上一轮还在发送」
    // 然后以为坏了。这里先排队等它跑完（最多 [WAIT_FOR_PREVIOUS_MS]），再跑自己这一轮。
    val waitUntil = System.currentTimeMillis() + WAIT_FOR_PREVIOUS_MS
    while (!renewRunning.compareAndSet(false, true)) {
        if (System.currentTimeMillis() >= waitUntil) return error("上一轮还在发送，请稍候再试")
        runCatching { Thread.sleep(QUEUE_POLL_MS) }
    }
    return try {
        runRenewLocked(context, respectEnabled, only, ignoreSentToday, repeatCount)
    } finally {
        renewRunning.set(false)
    }
}

internal fun runRenewLocked(
    context: Context,
    respectEnabled: Boolean,
    only: Set<String> = emptySet(),
    ignoreSentToday: Boolean = false,
    repeatCount: Int = 1,
): JSONObject {
    val config = ConfigClient.read(context) ?: return error("配置读取失败（模块未安装或未启用）")
    if (respectEnabled && !config.enabled) return error("总开关未开启")
    if (AccountBridge.getCurrentUserId().isNullOrBlank()) return error("未登录")

    val conversations = loadConversations() ?: return error("会话列表获取失败（IM 未初始化或链路变动）")
    // 空列表是 IM 还没加载完，不等于「今天没有要续的」：当成成功会让当天不再重试
    if (conversations.length() == 0) return error("会话列表还没加载完（0 条），稍后再试")
    val today = dayKey(Date())

    val sent = JSONArray()
    val skipped = JSONArray()
    val failed = JSONArray()
    // 脚本对沙盒与宏的改动：这一轮跑完再统一落盘
    val pendingSandbox = mutableListOf<com.tiantong.spark.app.config.model.SandboxVar>()
    val pendingSandboxDeletes = mutableListOf<Pair<String, String>>()
    val macroOps = mutableListOf<SparkScriptEngine.MacroOp>()

    // 第一遍：只筛选，不发送 —— 先算出这轮「待续」有几个，提示要用它
    val candidates = mutableListOf<JSONObject>()
    for (index in 0 until conversations.length()) {
        val conversation = conversations.optJSONObject(index) ?: continue
        val isGroup = conversation.optBoolean("isGroup")
        val conversationId = conversation.optString("conversationId")
        if (conversationId.isEmpty()) continue
        if (only.isNotEmpty()) {
            // 手动点名：只发这些，不再看抖音判定与手动归属
            if (conversationId !in only) continue
        } else {
            // cc 版：群聊与单聊同等参与自动续火花（不再要求先手动移入）
            // 「需要续」来自抖音判定，可被管理界面的手动移动覆盖
            if (conversationId in config.forcedSkip) continue
            val spark = conversation.optJSONObject("spark")
            val needsRenewal =
                conversationId in config.forcedRenew ||
                    (spark?.optBoolean("hasSpark") == true && spark.optBoolean("needsRenewal"))
            if (!needsRenewal) continue
        }
        candidates += conversation
    }
    HostTrace.add(context, "待续 ${candidates.size} 个")
    if (candidates.isEmpty()) {
        val sentTodayCount = config.sentDays.count { it.value == today }
        val hint =
            if (sentTodayCount > 0) {
                "（今日已完成：已发 $sentTodayCount 个）"
            } else {
                "（今天没有需要续火花的会话）"
            }
        com.tiantong.spark.core.DouToastHelper.show(context, hint)
        return JSONObject()
            .put("ok", true)
            .put("today", today)
            .put("sent", sent)
            .put("skipped", skipped)
            .put("failed", failed)
    }
    // 待续拆两层：
    //  待发        = 这轮确实要发的（今天还没发）
    //  待对方回复 = 今天已经发过、火花还是灰的（state 2/3）—— 已经互动过的（state=1）不算
    // （手动移进「已选目标」的会议即使已续也在 candidates 里，所以这里要按 state 过滤一遍）
    val waitingReply =
        candidates.count { conv ->
            val spark = conv.optJSONObject("spark")
            config.sentDays[conv.optString("conversationId")] == today &&
                spark != null &&
                spark.optInt("state", -1) in 2..3
        }
    val toSend = candidates.count { config.sentDays[it.optString("conversationId")] != today }
    HostTrace.add(context, "待发 $toSend 个，待对方回复 $waitingReply 个")
    // 只有真的有东西要发/在等，才弹提示；否则静默（结果提示会覆盖）
    if (toSend > 0 || waitingReply > 0) {
        val pendingHint =
            if (waitingReply == 0) {
                "（待发 $toSend 个）"
            } else {
                "（待发 $toSend 个 · 待对方回复 $waitingReply 个）"
            }
        com.tiantong.spark.core.DouToastHelper.show(context, pendingHint)
    }

    // 第二遍：真正发送
    // 同一轮里同一个会话只发一次：IM 列表万一返回重复项，也不会发两条
    val sentThisRun = mutableSetOf<String>()
    for (conversation in candidates) {
        val isGroup = conversation.optBoolean("isGroup")
        val conversationId = conversation.optString("conversationId")
        if (!sentThisRun.add(conversationId)) continue
        // 每发一条前重新读一次：并发的那一轮可能刚把 sentDays 写掉
        val alreadySentToday =
            !ignoreSentToday && ConfigClient.read(context)?.sentDays?.get(conversationId) == today
        if (alreadySentToday) {
            skipped.put(JSONObject().put("conversationId", conversationId).put("reason", "今日已发送"))
            continue
        }

        val name = displayName(conversation)
        val script = scriptFor(config, conversationId)
        if (script.isEmpty()) {
            // 没写文案、也没挂宏：这个会话确实没东西可发
            skipped.put(JSONObject().put("conversationId", conversationId).put("name", name).put("reason", "没有文案"))
            continue
        }
        val spark = conversation.optJSONObject("spark")
        val rendered =
            runCatching {
                SparkScriptEngine.renderFull(
                    com.tiantong.spark.app.script.TemplateVarPrelude.of(
                        config.templateVarsFor(conversationId),
                    ) + script,
                    buildVariables(conversation, spark),
                    sandboxEnv(context, config, conversationId, conversations, only, ignoreSentToday),
                    templateNameOf(config, conversationId),
                    parentTemplateNameOf(config, conversationId),
                )
            }
                .getOrElse { error ->
                    if (error is EmptyResultException) {
                        // 空结果 = 这条不发（跳过，不算失败），但还是把 Skipped 回给脚本注册的回调
                        skipped.put(
                            JSONObject().put("conversationId", conversationId).put("name", name)
                                .put("reason", "没有内容"),
                        )
                        fireResultCallbacks(
                            error.callbacks, conversationId, SparkScriptEngine.SEND_SKIPPED, "没有内容",
                            pendingSandbox, pendingSandboxDeletes, macroOps,
                        )
                    } else {
                        failed.put(
                            JSONObject().put("conversationId", conversationId).put("name", name)
                                .put("error", error.message ?: error.toString()),
                        )
                    }
                    continue
                }
        pendingSandbox.addAll(rendered.sandboxWrites)
        pendingSandboxDeletes.addAll(rendered.sandboxDeletes.map { it.owner to it.key })
        // 脚本没给目标就默认当前会话
        macroOps +=
            rendered.macroOps.map { op -> if (op.targetId.isBlank()) op.copy(targetId = conversationId) else op }
        val text = rendered.text

        // 分段发送：脚本用 ctx.commit() 切段、push({type:"emoji"}) 插表情；
        // 没分段时退化成「整条 text 发一次」，与老行为完全一致。
        val segments =
            rendered.segments.ifEmpty { listOf(SparkScriptEngine.SendSegment("text", text = text)) }
        val segResults = JSONArray()
        var sentAny = false
        var allOk = true
        var firstReason = ""
        var lastMsgId = 0L
        // 补发轮会要求「同一会话一次连发几条相同文案」，这里按段整组重复
        repeat(repeatCount.coerceAtLeast(1)) {
        segments.forEach { seg ->
            val ok: Boolean
            val errorText: String
            if (seg.kind == "text") {
                val outcome =
                    runCatching { JSONObject(ImMsgBridge.sendTextJson(conversationId, seg.text, 0L, "")) }.getOrNull()
                ok = outcome != null && outcome.optBoolean("ok")
                errorText = outcome?.optString("error").orEmpty().ifBlank { "发送失败" }
                if (ok) lastMsgId = outcome?.optLong("msgId") ?: lastMsgId
                segResults.put(
                    JSONObject().put("handle", seg.handle).put("kind", "text").put("ok", ok)
                        .put("reason", if (ok) "" else errorText),
                )
            } else {
                val content = emojiContentFor(context, seg.name)
                if (content == null) {
                    ok = false
                    errorText = "未知表情: ${seg.name}"
                    segResults.put(
                        JSONObject().put("handle", seg.handle).put("kind", "emoji").put("ok", false)
                            .put("name", seg.name).put("reason", errorText),
                    )
                } else {
                    val outcome =
                        runCatching { JSONObject(ImMsgBridge.sendEmojiJson(conversationId, content)) }.getOrNull()
                    ok = outcome != null && outcome.optBoolean("ok")
                    errorText = outcome?.optString("error").orEmpty().ifBlank { "表情发送失败" }
                    if (ok) lastMsgId = outcome?.optLong("msgId") ?: lastMsgId
                    segResults.put(
                        JSONObject().put("handle", seg.handle).put("kind", "emoji").put("ok", ok)
                            .put("name", seg.name).put("reason", if (ok) "" else errorText),
                    )
                }
            }
            if (ok) sentAny = true else {
                allOk = false
                if (firstReason.isEmpty()) firstReason = errorText
            }
        }
        }
        val success = allOk && sentAny
        val reason = if (success) "" else firstReason.ifBlank { "发送失败" }
        fireResultCallbacks(
            rendered.callbacks,
            conversationId,
            if (success) SparkScriptEngine.SEND_SUCCEED else SparkScriptEngine.SEND_FAILED,
            reason,
            pendingSandbox,
            pendingSandboxDeletes,
            macroOps,
            segmentsJson = segResults.toString(),
        )
        if (success) {
            ConfigClient.markSent(context, conversationId, today)
            sent.put(
                JSONObject().put("conversationId", conversationId).put("name", name)
                    .put("text", text).put("msgId", lastMsgId)
                    .put("segments", segResults.length()),
            )
        } else {
            failed.put(
                JSONObject().put("conversationId", conversationId).put("name", name)
                    .put("error", reason.ifBlank { "发送失败" })
                    .put("segments", segResults),
            )
        }
    }

    applyScriptMutations(context, config, pendingSandbox, pendingSandboxDeletes, macroOps)
    val skippedCount = skipped.length()
    val sentCount = sent.length()
    val failedCount = failed.length()
    // 结果提示分三种：真的发了 / 全是今天已发（对方没回） / 什么都没发生
    val text =
        when {
            sentCount > 0 ->
                "（抖音续火花cc：已发 $sentCount 个" +
                    (if (skippedCount > 0) "，跳过 $skippedCount 个" else "") +
                    (if (failedCount > 0) "，失败 $failedCount 个" else "") + "）"
            failedCount > 0 -> "（抖音续火花cc：$failedCount 个发送失败，其余今天已发）"
            skippedCount > 0 -> "（今天这 $skippedCount 个都发过了，等对方回）"
            else -> "（今天没有需要续火花的会话）"
        }
    com.tiantong.spark.core.DouToastHelper.show(context, text)

    return JSONObject()
        .put("ok", true)
        .put("today", today)
        .put("sent", sent)
        .put("skipped", skipped)
        .put("failed", failed)
}
