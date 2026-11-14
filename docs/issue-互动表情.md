# 功能请求：支持发送抖音「互动表情」（如「续火花」火焰贴纸）

## 一、背景 / 现状

模块当前的发送能力是**纯文本**（msgType=7），脚本通过 `ctx.result.push("...")` 产出字符串，
最终由 `ImMsgBridge.sendTextJson()` 构造 `TextContent` 发给抖音 IM SDK。

内置模板 `[续火花吧]` 发的也是文本 —— 它是抖音的**文本表情码**，由抖音客户端自己渲染成
「小火人举牌」贴图。这条路对普通表情包有效。

**问题**：抖音表情面板里还有一类「**互动表情**」（面板顶部 tab 显示 `已选中，互动表情`），
例如：续火花 / 比心 / 在干嘛 / 笑死 / 麻了 / 躺平 / 摇骰子 / 猜拳 / 开心 / 嗨 / 吹泡泡 /
黑人问号 / 早上好 / 晚上好 / 早点睡。

这些**不是文本表情码**，是独立的富消息类型。用文本码发 `[续火花]`，对方只会收到一条
写着「[续火花]」三个字的普通文字气泡，不会出现火焰贴纸。

而「续火花」火焰贴纸恰恰是抖音官方引导用户续火花的互动方式，比发文字更符合场景。

### 实测对照

| 发送内容 | 结果 |
| --- | --- |
| `ctx.result.push("[续火花吧]")` | ✅ 小火人举牌表情（文本码渲染） |
| `ctx.result.push("[续火花]")` | ❌ 纯文字气泡，没有任何贴图 |
| 面板里点「互动表情 → 续火花」 | ✅ 火焰贴纸（msgType=5 富消息） |

---

## 二、抓包结论

### 测试环境

| 项 | 值 |
| --- | --- |
| 抖音版本 | **41.0.0**（versionCode 410001，minSdk 23 / targetSdk 34） |
| 机型 | Xiaomi Mi 10（`umi`，arm64-v8a） |
| 系统 | Android 13（API 33） |
| 框架 | LSPosed **v2.2.1 (7912)**，Zygisk 模式，libxposed API 102 |
| 抓包工具 | frida 16.7.19（frida-server 改名 `fsd` + 换端口 8899 绕过检测） |
| 抓包日期 | 2026-11-14 |

### 抓包方法

抖音有反调试：直接用 `frida-server` 原名 + 默认端口 27042，
`attach` 会超时、`spawn` 会卡死。**改名 + 换端口**后可正常 attach（约 2.6 秒）。

```bash
# 手机侧（root）
cp /data/local/tmp/frida-server /data/local/tmp/fsd
chmod 755 /data/local/tmp/fsd
su -c '/data/local/tmp/fsd -D -l 127.0.0.1:8899 &'
# 电脑侧
adb forward tcp:8899 tcp:8899
frida -H 127.0.0.1:8899 -f com.ss.android.ugc.aweme -l dump.js
```

hook 点选 `Message$Builder.build()`（**不是** `IMessageUtils.sendMessage` —— 互动表情不走那里）：

```javascript
Java.perform(function () {
  var MB = Java.use("com.bytedance.im.core.model.Message$Builder");
  var ob = MB.build.overloads[0];
  ob.implementation = function () {
    var r = ob.call(this);
    try {
      send({ tag: "build", msgType: r.getMsgType(), content: String(r.getContent()) });
    } catch (e) {}
    return r;
  };
  send({ tag: "hooked" });
});
```

然后在抖音里手动点一次「表情 → 互动表情 → 续火花」，控制台打印出完整参数。

### 抓到的真实参数

```
msgType = 5
```

```json
{
  "danmaku_id": "",
  "danmaku_text": "",
  "display_name": "续火花",
  "emoji_from": "lite",
  "id": "0",
  "emoji_source": "",
  "emoji_type": "lite_emoji",
  "height": 100,
  "image_id": 0,
  "image_type": "png",
  "match_uid": "",
  "message_reply_display_type": 0,
  "package_id": 0,
  "show_notice": false,
  "source_schema": "",
  "resource_type": 4,
  "sticker_type": 12,
  "top_desc_scene_type": 0,
  "top_desc_uid_match": "",
  "updateConversationTime": true,
  "url": {
    "height": 100,
    "data_size": 0,
    "uri": "https://p11-sign.douyinpic.com/obj/im-resource/1687263281313-ts-e7bbade781abe88ab12e706e67?lk3s=91c5b7cb&x-expires=1826161200&x-signature=MeMoJJnToGOJ%2BdDUy05x7wcKqB8%3D&from=2445653963&s=im_111&se=false&sc=image&biz_tag=aweme_im&l=202611141130529D44EC529AF2A0840C9D",
    "url_list": [
      "https://p11-sign.douyinpic.com/obj/im-resource/1687263281313-ts-e7bbade781abe88ab12e706e67?lk3s=91c5b7cb&x-expires=1826161200&x-signature=MeMoJJnToGOJ%2BdDUy05x7wcKqB8%3D&from=2445653963&s=im_111&se=false&sc=image&biz_tag=aweme_im&l=202611141130529D44EC529AF2A0840C9D"
    ],
    "width": 100
  },
  "version": "2",
  "width": 100,
  "createdAt": 0,
  "is_card": false,
  "msgHint": "",
  "aweType": 507
}
```

### 几个关键发现

1. **msgType = 5**（大表情 / 贴纸），不是文本的 7
2. **`id` 恒为 `"0"`** —— 不依赖表情库自增 id，
   靠 `sticker_type: 12` + `emoji_type: "lite_emoji"` 组合区分是哪个互动表情
3. **content 类名已在源码里出现过**：
   `com.ss.android.ugc.aweme.im.sdk.chat.model.StickerEmojiContent`
   （`ImMsgBridge.extractMessage()` 的 msgType=5 分支本来就在解析
   `id / width / height / display_name / emoji_source` 这几个字段）
4. **url 签名有效期约 1 年**（本次样本 `x-expires` 到 2027-11-14），可直接内置
5. 互动表情**不走** `IMessageUtils.sendMessage()` 那条路径（hook 它抓不到），
   但宿主最终仍用 `Message$Builder.build()` 产出消息对象 —— 说明自己构造 message
   再调 `sendMessage()` 是**可行**的（已在本机验证成功）

---

## 三、怎么改

### 3.1 为什么改动很小

看 `ImMsgBridge.sendTextJson()` 的关键几行：

```kotlin
buildMethods.first { it.name == "conversation" && it.parameterCount == 1 }.invoke(builder, conversation)
buildMethods.first { it.name == "msgType"      && it.parameterCount == 1 }.invoke(builder, MSG_TYPE_TEXT)
buildMethods.first { it.name == "content"      && it.parameterCount == 1 }.invoke(builder, contentJson)
val message = buildMethods.first { it.name == "build" && it.parameterCount == 0 }.invoke(builder)
```

**`msgType` 和 `content` 本来就是两个独立参数**分开喂给 builder 的，
后面的 `contentObj` 回填、ext 填充、`sendMessage()` 全都不关心消息类型。
所以发互动表情 = 换一个 msgType + 换一份 content。

### 3.2 具体代码

在 `ImMsgBridge` 加常量：

```kotlin
/** 互动表情（大表情包）的协议类型。 */
private const val MSG_TYPE_STICKER = 5

/** 互动表情 content 类（msgType=5）。 */
private const val STICKER_CONTENT =
    "com.ss.android.ugc.aweme.im.sdk.chat.model.StickerEmojiContent"
```

新增发送方法（照 `sendTextJson` 抄，差异只有 msgType 和 content）：

```kotlin
fun sendStickerJson(
    conversationId: String,
    contentJson: String,
): String {
    if (conversationId.isBlank()) return errorJson("会话 ID 为空")
    if (contentJson.isBlank()) return errorJson("表情内容为空")
    if (AccountBridge.getCurrentUserId().isNullOrBlank()) return errorJson("未登录")

    val conversation = resolveConversation(conversationId)
        ?: return errorJson("会话不存在或未加载（conversationId=$conversationId）")

    return runCatching {
        // 1. contentObj：Gson 反序列化成宿主的表情 content 对象（发送前的非空校验要用）
        val contentObj =
            runCatching {
                val contentClass = Class.forName(STICKER_CONTENT, false, HostRuntime.requireClassLoader())
                val gsonClass = Class.forName(GSON_UTIL, false, HostRuntime.requireClassLoader())
                val gson = gsonClass.getDeclaredField("INSTANCE").get(null)!!
                gsonClass.methods
                    .first {
                        it.name == "fromJson" && it.parameterCount == 2 &&
                            it.parameterTypes[0] == String::class.java
                    }.invoke(gson, contentJson, contentClass)
            }.getOrNull()

        // 2. 构造消息
        val builder = createMessageBuilder() ?: return errorJson("消息构造器获取失败")
        val buildMethods = builder.javaClass.methods
        buildMethods.first { it.name == "conversation" && it.parameterCount == 1 }.invoke(builder, conversation)
        buildMethods.first { it.name == "msgType"      && it.parameterCount == 1 }.invoke(builder, MSG_TYPE_STICKER)
        buildMethods.first { it.name == "content"      && it.parameterCount == 1 }.invoke(builder, contentJson)
        val message = buildMethods.first { it.name == "build" && it.parameterCount == 0 }.invoke(builder)
            ?: return errorJson("消息构造失败")

        // 3. contentObj 回填（与宿主一致）
        if (contentObj != null) {
            Reflect.fieldsOf(message.javaClass)["contentObj"]?.set(message, contentObj)
            Reflect.fieldsOf(message.javaClass)["contentUpdateFrom"]?.set(message, "message_builder")
        }

        // 4. 客户端发送 ext
        val now = System.currentTimeMillis()
        @Suppress("UNCHECKED_CAST")
        val ext = Reflect.getter(message, "getExt") as? MutableMap<String, String>
        ext?.put("old_client_message_id", now.toString())
        ext?.put("im_client_send_msg_time", now.toString())
        ext?.put("a:ntp_ready", "0")

        // 5. 发送（与 sendTextJson 完全一致）
        val utils = messageUtils() ?: return errorJson("消息发送工具获取失败")
        val listener = SendListener(Class.forName(REQUEST_LISTENER, false, HostRuntime.requireClassLoader()))
        val sendMethod = utils.javaClass.methods.firstOrNull {
            it.name == "sendMessage" && it.parameterCount == 3
        } ?: return errorJson("发送方法未找到")
        sendMethod.invoke(utils, message, HashMap<String, String>(), listener.proxy)
        listener.await()?.let { return errorJson(it) }

        val sent = listener.sentMessage ?: message
        val msgId = (Reflect.getter(sent, "getMsgId") as? Number)?.toLong() ?: 0L
        JSONObject()
            .put("ok", true)
            .put("conversationId", conversationId)
            .put("msgId", msgId)
            .put("sticker", true)
            .toString()
    }.getOrElse {
        ModLog.e("sendSticker 失败", it)
        errorJson("发送异常: ${it.cause ?: it}")
    }
}
```

### 3.3 接到脚本层（二选一）

**方案 A：文本标记（改动最小，我目前用的）**

发送前判断 `ctx.result` 里是不是特殊标记：

```kotlin
if (StickerCatalog.isStickerMark(text)) {
    JSONObject(ImMsgBridge.sendStickerJson(conversationId, StickerCatalog.byName(name)))
} else {
    JSONObject(ImMsgBridge.sendTextJson(conversationId, text, 0L, ""))
}
```

脚本里写：

```js
ctx.result.push("[STICKER:续火花]");
```

**方案 B：独立 API（更干净，但要动脚本引擎）**

在 `ctx` 上挂一个方法：

```js
ctx.sticker("续火花");
```

需要在 `SparkScriptEngine` 构造 `ctx` 时注入这个函数，改动比 A 大一些，
但语义更清晰，也不会有"标记泄漏成文字"的风险。

### 3.4 表情目录

建议内置一个 `表情名 → content JSON` 的表，而不是让用户手写 JSON：

```kotlin
object StickerCatalog {
    private val CONTENT: Map<String, String> = mapOf(
        "续火花" to """{ ...上面抓到的 JSON... }""",
        // 以后抓到了再加：比心 / 猜拳 / 摇骰子 ...
    )
    fun byName(name: String): String? = CONTENT[name.trim()]
    fun names(): List<String> = CONTENT.keys.toList()
}
```

---

## 四、验证结果

在本机（环境见第二节）实测：

| 步骤 | 结果 |
| --- | --- |
| 编译改动后的模块 | ✅ 通过 |
| 装到设备并注入抖音 | ✅ LSPosed 日志有 `onModuleLoaded` |
| 手动发送「续火花表情」模板 | ✅ **对方收到火焰贴纸**（不是文字） |
| 对方（自己另一账号）看聊天记录 | ✅ 显示为互动表情，点击有动效 |

截图对比：

- 发 `[续火花吧]` → 小火人举牌（红色/紫色小人拿牌子）
- 发互动表情 → 橙色火焰贴纸

---

## 五、可以扩展的方向

同一套机制可以吃下**所有**互动表情，只要抓一条对应 content：

| 面板位置 | 表情名 | 状态 |
| --- | --- | --- |
| 第 1 格 | 续火花 | ✅ 已抓 |
| 第 2 格 | 比心 | 待抓 |
| 第 3 格 | 在干嘛 | 待抓 |
| 第 4 格 | 笑死 | 待抓 |
| 第 5 格 | 麻了 | 待抓 |
| 第二行 | 躺平 / 摇骰子 / 猜拳 / 开心 / 嗨 | 待抓 |
| 第三行 | 吹泡泡 / 黑人问号 / 早上好 / 晚上好 / 早点睡 | 待抓 |

抓法完全一样：改 `STICKER` 常量，跑一次 hook，点一下对应按钮。

注意 `摇骰子` / `猜拳` 可能有随机结果字段，需要额外观察。

---

## 六、风险

1. **url 签名会过期**（约 1 年）。过期后贴纸不显示，需要重新抓一条。
   如果抖音服务端会按 msgType + sticker_type 重新下发资源，可能不受影响 —— 未验证。
2. **抖音升级可能改字段**，特别是 `sticker_type: 12` 这类魔法数字。
3. **风控**：连续给多个会话发同一条互动表情，可能比文本更容易被识别为脚本。
   建议加随机间隔。

---

## 七、附：完整抓包脚本

```javascript
// dump-sticker.js
// 用法：frida -H 127.0.0.1:8899 -f com.ss.android.ugc.aweme -l dump-sticker.js
// 然后在抖音里手动点一次「表情 → 互动表情 → 续火花」
Java.perform(function () {
  var MB = Java.use("com.bytedance.im.core.model.Message$Builder");
  var ob = MB.build.overloads[0];
  ob.implementation = function () {
    var r = ob.call(this);
    try {
      var t = r.getMsgType();
      var c = String(r.getContent());
      if (t === 5 || c.indexOf("sticker_type") >= 0 || c.indexOf("lite_emoji") >= 0) {
        console.log("=== STICKER CAUGHT ===");
        console.log("msgType = " + t);
        console.log("content = " + c);
      }
    } catch (e) {}
    return r;
  };
  console.log("[hooked] Message$Builder.build");
});
```

如果要顺便验证自己构造 message 能不能发出去，再把 `IMessageUtils.sendMessage` 也 hook 上，
看有没有触发即可。
