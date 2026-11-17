package com.worldmargin.autospark.app.config

import com.worldmargin.autospark.app.config.model.*
import org.json.JSONArray
import org.json.JSONObject
import com.worldmargin.autospark.app.config.model.TemplateVar
import com.worldmargin.autospark.app.config.model.TemplateGroup
import com.worldmargin.autospark.app.config.model.ConversationTemplate
import com.worldmargin.autospark.app.config.model.MacroScript
import com.worldmargin.autospark.app.config.model.UserFunction
import com.worldmargin.autospark.app.config.model.ConversationSnapshot
import com.worldmargin.autospark.app.config.model.SandboxVar
import com.worldmargin.autospark.app.config.model.SparkTag

object ConfigCodec {
    private const val KEY_ENABLED = "enabled"
    private const val KEY_GROUPS = "groups"
    private const val KEY_CONVERSATIONS = "conversations"
    private const val KEY_SENT_DAYS = "sentDays"
    private const val KEY_SNAPSHOTS = "snapshots"
    private const val KEY_FORCED_RENEW = "forcedRenew"
    private const val KEY_FORCED_SKIP = "forcedSkip"
    private const val KEY_HOST_NOTE = "hostNote"
    private const val KEY_HOST_NOTE_AT = "hostNoteAt"
    private const val KEY_HOST_ALIVE_AT = "hostAliveAt"
    private const val KEY_GUIDE_DISMISSED = "guideDismissed"
    private const val KEY_GUIDE_SEARCHED = "guideSearched"
    private const val KEY_GUIDE_SHARED = "guideShared"
    private const val KEY_ALARM_HOUR = "alarmHour"
    private const val KEY_ALARM_MINUTE = "alarmMinute"
    private const val KEY_ALARM_ASKED = "alarmAsked"
    private const val KEY_CHECK_AT_ALARM_ONLY = "checkAtAlarmOnly"
    private const val KEY_MACROS = "macros"
    private const val KEY_FUNCTIONS = "functions"
    private const val KEY_TAGS = "tags"
    private const val KEY_CONVERSATION_TAGS = "conversationTags"
        private const val KEY_SANDBOX = "sandbox"

    private const val INHERIT_BLANK = "blank"

    /** 模板变量数组 → JSON（配置文件与「变量编辑页」的来回传参共用同一份编码）。 */
    fun encodeVars(variables: List<TemplateVar>): JSONArray =
        JSONArray().apply {
            variables.forEach { variable -> put(JSONObject().put("name", variable.name).put("expr", variable.expr)) }
        }

    /** JSON → 模板变量数组（缺字段按空串处理）。 */
    fun decodeVars(array: JSONArray?): List<TemplateVar> =
        if (array == null) {
            emptyList()
        } else {
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { TemplateVar(it.optString("name"), it.optString("expr")) }
            }
        }

    /** 会话快照数组 → JSON（跨进程写快照与管理界面存盘共用同一份编码，字段不会漏）。 */
    fun encodeSnapshots(snapshots: List<ConversationSnapshot>): JSONArray {
        val array = JSONArray()
        snapshots.forEach { snapshot ->
            array.put(
                JSONObject()
                    .put("conversationId", snapshot.conversationId)
                    .put("name", snapshot.name)
                    .put("remark", snapshot.remark)
                    .put("avatar", snapshot.avatar)
                    .put("peerUid", snapshot.peerUid)
                    .put("days", snapshot.days)
                    .put("state", snapshot.state)
                    .put("stateText", snapshot.stateText)
                    .put("recoverDays", snapshot.recoverDays)
                    .put("recoverNeedDays", snapshot.recoverNeedDays)
                    .put("flameText", snapshot.flameText)
                    .put("expireText", snapshot.expireText)
                    .put("hasSpark", snapshot.hasSpark)
                    .put("needsRenewal", snapshot.needsRenewal)
                    .put("isGroup", snapshot.isGroup)
                    .put("updatedAt", snapshot.updatedAt)
                    .put("missCount", snapshot.missCount),
            )
        }
        return array
    }

    fun encode(config: SparkConfig): String {
        val groups = JSONArray()
        config.groups.forEach { group ->
            groups.put(
                JSONObject()
                    .put("id", group.id)
                    .put("name", group.name)
                    .put("body", group.body)
                    .put("updatedAt", group.updatedAt)
                    .put("variables", encodeVars(group.variables)),
            )
        }

        val conversations = JSONArray()
        config.conversations.forEach { template ->
            conversations.put(
                JSONObject()
                    .put("conversationId", template.conversationId)
                    .put("inheritFrom", template.inheritFrom ?: INHERIT_BLANK)
                    .put("body", template.body)
                    .put("parentHashAtSync", template.parentHashAtSync)
                    .put("autoSync", template.autoSync)
                    .put("updatedAt", template.updatedAt)
                    .put("selectedAt", template.selectedAt)
                    .put("variables", encodeVars(template.variables)),
            )
        }

        val sentDays = JSONObject()
        config.sentDays.forEach { (conversationId, day) -> sentDays.put(conversationId, day) }

        val snapshots = encodeSnapshots(config.snapshots)

        val macros = JSONArray()
        config.macros.forEach { macro ->
            macros.put(
                JSONObject()
                    .put("id", macro.id)
                    .put("name", macro.name)
                    .put("body", macro.body)
                    .put("appliesTo", JSONArray(macro.appliesTo.toList()))
                    .put("templateOnly", JSONArray(macro.templateOnly.toList()))
                    .put("updatedAt", macro.updatedAt),
            )
        }

        val sandbox = JSONArray()
        config.sandbox.forEach { item ->
            sandbox.put(JSONObject().put("owner", item.owner).put("key", item.key).put("value", item.value))
        }

        val functions = JSONArray()
        config.functions.forEach { fn ->
            functions.put(
                JSONObject()
                    .put("id", fn.id)
                    .put("name", fn.name)
                    .put("params", fn.params)
                    .put("body", fn.body)
                    .put("desc", fn.desc)
                    .put("updatedAt", fn.updatedAt),
            )
        }

        val tags = JSONArray()
        config.tags.forEach { item ->
            tags.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("color", item.color)
                    .put("createdAt", item.createdAt),
            )
        }

        val conversationTags = JSONObject()
        config.conversationTags.forEach { (conversationId, ids) ->
            conversationTags.put(conversationId, JSONArray(ids.toList()))
        }

        return JSONObject()
            .put(KEY_ENABLED, config.enabled)
            .put(KEY_GROUPS, groups)
            .put(KEY_CONVERSATIONS, conversations)
            .put(KEY_SENT_DAYS, sentDays)
            .put(KEY_SNAPSHOTS, snapshots)
            .put(KEY_FORCED_RENEW, JSONArray(config.forcedRenew.toList()))
            .put(KEY_FORCED_SKIP, JSONArray(config.forcedSkip.toList()))
            .put(KEY_MACROS, macros)
            .put(KEY_FUNCTIONS, functions)
            .put(KEY_SANDBOX, sandbox)
            .put(KEY_TAGS, tags)
            .put(KEY_CONVERSATION_TAGS, conversationTags)
            .put(KEY_HOST_NOTE, config.hostNote)
            .put(KEY_HOST_NOTE_AT, config.hostNoteAt)
            .put(KEY_HOST_ALIVE_AT, config.hostAliveAt)
            .put(KEY_GUIDE_DISMISSED, config.guideDismissed)
            .put(KEY_GUIDE_SEARCHED, config.guideSearched)
            .put(KEY_GUIDE_SHARED, config.guideShared)
            .put(KEY_ALARM_HOUR, config.alarmHour)
            .put(KEY_ALARM_MINUTE, config.alarmMinute)
            .put(KEY_ALARM_ASKED, config.alarmAsked)
            .put(KEY_CHECK_AT_ALARM_ONLY, config.checkAtAlarmOnly)
            .toString()
    }

    fun decode(text: String): SparkConfig = decodeOrNull(text) ?: SparkConfig()

    /**
     * 解析配置。
     *
     * **文件损坏（JSON 不合法）返回 null**，与「空文件」（返回空配置）区分开 ——
     * 否则 ConfigStore 会把一份损坏文件当成空配置，下一次 update 就把用户数据真正清空了。
     */
    fun decodeOrNull(text: String): SparkConfig? {
        if (text.isBlank()) return SparkConfig()
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null

        val groups = mutableListOf<TemplateGroup>()
        root.optJSONArray(KEY_GROUPS)?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                groups +=
                    TemplateGroup(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        body = item.optString("body"),
                        updatedAt = item.optLong("updatedAt"),
                        variables = decodeVars(item.optJSONArray("variables")),
                    )
            }
        }

        val conversations = mutableListOf<ConversationTemplate>()
        root.optJSONArray(KEY_CONVERSATIONS)?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val parent = item.optString("inheritFrom", INHERIT_BLANK)
                conversations +=
                    ConversationTemplate(
                        conversationId = item.optString("conversationId"),
                        inheritFrom = if (parent.isEmpty() || parent == INHERIT_BLANK) SparkConfig.BUILTIN_BLANK else parent,
                        body = item.optString("body"),
                        parentHashAtSync = item.optString("parentHashAtSync"),
                        autoSync = item.optBoolean("autoSync"),
                        updatedAt = item.optLong("updatedAt"),
                        selectedAt = item.optLong("selectedAt"),
                        variables = decodeVars(item.optJSONArray("variables")),
                    )
            }
        }

        val sentDays = mutableMapOf<String, String>()
        root.optJSONObject(KEY_SENT_DAYS)?.let { obj ->
            obj.keys().forEach { key -> sentDays[key] = obj.optString(key) }
        }

        val snapshots = mutableListOf<ConversationSnapshot>()
        root.optJSONArray(KEY_SNAPSHOTS)?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                snapshots +=
                    ConversationSnapshot(
                        conversationId = item.optString("conversationId"),
                        name = item.optString("name"),
                        remark = item.optString("remark"),
                        avatar = item.optString("avatar"),
                        peerUid = item.optString("peerUid"),
                        days = item.optInt("days"),
                        state = item.optInt("state"),
                        stateText = item.optString("stateText"),
                        recoverDays = item.optInt("recoverDays"),
                        recoverNeedDays = item.optInt("recoverNeedDays"),
                        flameText = item.optString("flameText"),
                        expireText = item.optString("expireText"),
                        hasSpark = item.optBoolean("hasSpark"),
                        needsRenewal = item.optBoolean("needsRenewal"),
                        isGroup = item.optBoolean("isGroup"),
                        updatedAt = item.optLong("updatedAt"),
                        missCount = item.optInt("missCount"),
                    )
            }
        }

        val macros = mutableListOf<MacroScript>()
        root.optJSONArray(KEY_MACROS)?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                macros +=
                    MacroScript(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        body = item.optString("body"),
                        appliesTo = item.readIdSet("appliesTo"),
                        templateOnly = item.readIdSet("templateOnly"),
                        updatedAt = item.optLong("updatedAt"),
                    )
            }
        }

        val sandbox = mutableListOf<SandboxVar>()
        root.optJSONArray(KEY_SANDBOX)?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                sandbox +=
                    SandboxVar(
                        owner = item.optString("owner"),
                        key = item.optString("key"),
                        value = item.optString("value"),
                    )
            }
        }

        val functions = mutableListOf<UserFunction>()
        root.optJSONArray(KEY_FUNCTIONS)?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                functions +=
                    UserFunction(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        params = item.optString("params"),
                        body = item.optString("body"),
                        desc = item.optString("desc"),
                        updatedAt = item.optLong("updatedAt"),
                    )
            }
        }

        val tags = mutableListOf<SparkTag>()
        root.optJSONArray(KEY_TAGS)?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isEmpty()) continue
                tags +=
                    SparkTag(
                        id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                        name = name,
                        color = item.optInt("color"),
                        createdAt = item.optLong("createdAt"),
                    )
            }
        }

        val conversationTags = mutableMapOf<String, Set<String>>()
        root.optJSONObject(KEY_CONVERSATION_TAGS)?.let { obj ->
            obj.keys().forEach { conversationId ->
                val ids =
                    obj.optJSONArray(conversationId)?.let { array ->
                        (0 until array.length())
                            .mapNotNull { index -> array.optString(index).takeIf { it.isNotEmpty() } }
                            .toSet()
                    }.orEmpty()
                if (ids.isNotEmpty()) conversationTags[conversationId] = ids
            }
        }

        return SparkConfig(
            enabled = root.optBoolean(KEY_ENABLED),
            groups = groups,
            conversations = conversations,
            sentDays = sentDays,
            snapshots = snapshots,
            forcedRenew = root.readIdSet(KEY_FORCED_RENEW),
            forcedSkip = root.readIdSet(KEY_FORCED_SKIP),
            macros = macros,
            functions = functions,
            sandbox = sandbox,
            tags = tags,
            conversationTags = conversationTags,
            hostNote = root.optString(KEY_HOST_NOTE),
            hostNoteAt = root.optLong(KEY_HOST_NOTE_AT),
            hostAliveAt = root.optLong(KEY_HOST_ALIVE_AT),
            guideDismissed = root.optBoolean(KEY_GUIDE_DISMISSED),
            guideSearched = root.optBoolean(KEY_GUIDE_SEARCHED),
            guideShared = root.optBoolean(KEY_GUIDE_SHARED),
            alarmHour = root.optInt(KEY_ALARM_HOUR, -1),
            alarmMinute = root.optInt(KEY_ALARM_MINUTE, 0),
            alarmAsked = root.optBoolean(KEY_ALARM_ASKED),
            checkAtAlarmOnly = root.optBoolean(KEY_CHECK_AT_ALARM_ONLY, true),
        )
    }

    private fun JSONObject.readIdSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf { id -> id.isNotEmpty() } }.toSet()
    }
}
