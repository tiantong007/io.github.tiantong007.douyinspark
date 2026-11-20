# 抖音续火花cc

抖音「自动续火花」LSPosed 模块。

**一句话**：给「抖音判定今天还没互动」的会话，自动发一条你自己写的私信，每个会话每天最多一条。

<!-- version-anchor: v2.2.0 (20008) -->
**版本 v2.2.0**（versionCode 20008）· Android 9+ / Root / LSPosed 2.x · 仅 arm64-v8a

---

## 目录

- [一、它能做什么](#一它能做什么)
- [二、安装](#二安装)
- [三、界面](#三界面)
- [四、总开关](#四总开关)
- [五、什么时候会发](#五什么时候会发)
- [六、定时唤醒（锁屏也能发，需要 root）](#六定时唤醒锁屏也能发需要-root)
- [七、模板、变量与保留](#七模板变量与保留)
- [八、脚本 API](#八脚本-api)
- [九、编辑器](#九编辑器)
- [十、用 AI 写脚本](#十用-ai-写脚本)
- [十一、常见问题](#十一常见问题)
- [十二、数据与文件](#十二数据与文件)
- [附录 A：一次发送的完整时序](#附录-a一次发送的完整时序)
- [附录 B：构建](#附录-b构建)
- [附录 C：来源与许可](#附录-c来源与许可)

---

## 一、它能做什么

### 续火花

自动给抖音判定「今天还没互动」的会话发一条私信，每个会话每天最多一条。

- **三页视图**：未燃（该续的）/ 已燃（今天已互动）/ 全部（有火花的）
- **无火花卡片**：全部页底部，可把没火花的会话手动移入未燃，之后每天自动发
- **被隐藏卡片**：连续 3 次没在抖音出现的会话收在这里，可恢复
- **批量操作**：勾选后移入/移出，按钮跟着页面变
- **状态可见**：`火花 410 天 · 重燃中 2/3 · 今日已发 · 6 天后消失`

### 互动表情

模板里写 `ctx.result.push({ type: "sticker", name: "续火花" })`，发出的是抖音的**互动表情**（火焰贴纸，`msgType=5`），不是文字。

```js
var who = ctx.io.name || "朋友";
ctx.result.push(who + "，火花 " + ctx.io.days + " 天啦");
ctx.result.push({ type: "sticker", name: "续火花" });
```

v2.2.0 起表情目录从抖音自己的配置里读（当前 21 个），`ctx.sticker.list()` 可以看全部名字。老的 `[STICKER:续火花]` 写法仍然能用。

### 定时唤醒

模块跑在抖音进程里，抖音不启动就没有代码在跑 —— 这是锁屏自动发的唯一障碍。用 `AlarmManager` 每天到点 + root `monkey` 拉起抖音，发送本身不需要亮屏、不需要解锁。**需要 root。**见[第六节](#六定时唤醒锁屏也能发需要-root)。

### 模板

- 2 个内置：**续火花表情**、**通用问候**
- 自定义模板，支持**继承**（跟随父模板）与**克隆**（复制后独立）
- 两层变量（模板层 + 会话层），值是 JS 表达式

### 可靠性

- 每会话每天最多一条（`sentDays` 台账，跨天重置）
- 配置**原子写**（临时文件 + rename）
- 配置损坏时自动备份为 `spark_config.json.broken-<时间戳>` 并**拒绝覆盖**
- 三态回执：Succeed / Skipped / Failed

---

## 二、安装

1. 安装 APK
2. LSPosed 里启用模块，勾选作用域「抖音」（`com.ss.android.ugc.aweme`）
   - 本模块声明了静态作用域，装完通常已自动勾好
3. **强制停止抖音再打开**（改完 APK 也必须重启抖音，否则跑的还是旧代码）
4. 打开模块 App 一次，确认「设置 → 宿主状态」有内容

要求：Android 9+、已 Root、LSPosed 2.x（API 101+）、仅 arm64-v8a。

升级时直接覆盖安装（同签名），配置保留。**换签名或卸载会丢配置**，请先在「设置 → 导出配置」备份。

---

## 三、界面

底部三个页签：**续火花 / 模板 / 设置**。

### 3.1 续火花

内层分三页，**每页的发送按钮只发该页的会话**：

| 页 | 含义 | 判定 |
|---|---|---|
| **未燃** | 火花没点亮，需要我去续 | `有火花 && 抖音判定今天还没互动`，或手动移入的 |
| **已燃** | 火花今天已点亮 | `有火花 && 今天已互动` |
| **全部** | 有火花的全部会话 | `有火花` |

列表按**单聊 / 群聊**分成两张卡片。

#### 每行显示什么

```
XXX                                   未写文案
火花 58 天 · 待发
```

```
XXX                                   继承：通用问候
火花 410 天 · 重燃中 2/3 · 今日已发 · 6 天后消失
```

| 状态 | 含义 |
|---|---|
| `待发` | 今天还没发，该发 |
| `今日已发` | 今天已经发过（**不代表对方回了**） |
| `重燃中 2/3` | 火花断了，需要连续互发 3 天恢复，已完成 2 天 |
| `6 天后消失` | 再不动手，火花彻底没了 |
| `手动移入` | 被你强制放进未燃 |
| `手动移出` | 被你强制跳过，不再自动发 |

> 文案全部取自抖音原生字段，不自己算。`flame_infos` 是**时间轴数组**，每元素对应一天：取「今天」那段的 `text`，不取 `recover_days` —— 后者在重燃场景下会和抖音界面数字不一致。

#### 勾选后的批量操作

长按整行或点勾选框选中，底部出现操作条。**按钮跟着页面变**：

| 当前页 | 显示 |
|---|---|
| 未燃 | 只有「移出未燃」 |
| 已燃 | 只有「移入未燃」 |
| 全部 | 两个都有 |

- **移入未燃** = 强制它进待发队列（哪怕抖音认为不需要续，哪怕它没火花）
- **移出未燃** = 强制跳过它（哪怕抖音认为需要续）

#### 底部的两张折叠卡片

**「无火花 N」**（只在「全部」页出现）

没建立火花的会话（`days=0`）。点某行「移入未燃」→ 进入待发队列，之后每天自动发。

> 建议：先挑 2~3 个试。抖音对「给陌生人批量发相似内容」比熟人敏感得多。

**「被隐藏 M」**（有才显示）

连续 3 次扫描没在抖音会话列表里出现的会话收在这里。可能是删好友、退群，也可能只是抖音加载慢被误判。

两个按钮：
- **恢复**：把未出现计数清零，让它回到列表（下次还扫不到会再次隐藏）
- **移出目标**：把它从「未燃」里摘掉

### 3.2 模板

一级模板都能折叠展开。**展开后：动作按钮在上，派生列表在下**。

```
通用问候                    派生 8  ▾
─────────────────────────────────
[编辑模板] [选会话继承] [克隆到会话]
─────────────────────────────────
XXX         跟随父模板
YYY         跟随父模板
```

#### 内置模板（2 个）

| 名字 | 内容 |
|---|---|
| **续火花表情** | 发抖音的「续火花」互动表情（火焰贴纸） |
| **通用问候** | 两句随机：`XXX，今天也来续一下火花吧` / `火花 N 天啦，XXX 别断～` |

内置模板不能直接改：点「添加为我的模板」复制成自己的。

#### 自定义模板

新建后可编辑、删除、选会话继承、克隆到会话。

### 3.3 设置

设置页最上面是**总开关**和**定时唤醒**（两块都在首屏，不用点进去），下面才是二级入口：

| 子页 | 用途 |
|---|---|
| 概览 | 未燃会话数、快照数（含已隐藏数）、宿主状态 |
| 手动归属 | 一键清除所有「手动移入/移出」标记 |
| 配置 | 导出 / 导入整份配置（换机、备份用） |
| 宿主状态 | 模块是否注入成功 |
| 新手引导 | 基础 / 进阶两条路线 |
| 关于 | 版本与配置文件位置 |

---

## 四、总开关

设置页 → 总开关 → 「自动续火花」，**默认关闭**。

**它只管自动轮，不拦手动点**：

| 场景 | 总开关 OFF | 总开关 ON |
|---|---|---|
| 抖音启动 | ❌ 只扫描不发 | ✅ 扫一轮并发送 |
| 抖音回前台 | ❌ 只刷新快照 | ✅ 扫一轮并发送 |
| 凌晨跨天 | ❌ 不补跑 | ✅ 自动补跑 |
| App 内点「发送」 | ✅ **能发** | ✅ 能发 |
| 抖音设置页「立即续火花」 | ✅ **能发** | ✅ 能发 |

关着也能扫描，所以你在 App 里能看到最新火花状态，只是不会真的发出去。

---

## 五、什么时候会发

触发时机：

1. 抖音**冷启动**（打开 App 后 5 秒）
2. 抖音**回到前台**（从后台切回来）
3. **跨天**（23:30 后加密检查，23:58~05:00 每 5 秒）
4. **定时唤醒**（见第六节）

发送规则：

| 条件 | 说明 |
|---|---|
| 只发「未燃」里的会话 | 已燃 / 全部页的按钮也能手动发，但自动轮只跑未燃 |
| 每会话每天最多一条 | `sentDays` 台账，跨天重置 |
| 群聊同样参与 | 和单聊一个待遇 |
| 抖音没在运行就发不出去 | 模块注入在抖音进程里 |

---

## 六、定时唤醒（锁屏也能发，需要 root）

模块的代码跑在**抖音进程里**（LSPosed 注入）。抖音不启动 = 没有任何代码在跑 —— 这是「锁屏自动发」唯一的技术障碍。

整条链路：

```
每天设定时间 → AlarmManager 叫醒本模块进程
             → root + monkey 拉起抖音（不亮屏、能清 stopped 标记）
             → 抖音进程启动 → Application.attach 被 hook
             → 扫描 → 发送 → 写 spark_last_auto_day
             → 模块检测到已发 → am force-stop 关掉抖音
```

**发送本身不需要亮屏、不需要解锁**，它只走 IM SDK 的发送链路。

### 6.1 为什么必须 root

拉起抖音这一步走的是系统自带的 `monkey`（AMS 注入通道），不走 `startActivity` —— 后者在 Android 10+ 会被 `backgroundActivityAllowed=false` 拦掉。

`monkey` 只接受 root 或 shell 身份。普通 App 身份执行会被系统直接 `Killed`：

```
su 10276 -c "monkey -p com.ss.android.ugc.aweme ... 1"   → Killed
su 0     -c "monkey -p com.ss.android.ugc.aweme ... 1"   → Events injected: 1
```

所以**没有 root 就没有定时唤醒**，设置页会显示「未获得 root 授权」，不做降级。

### 6.2 开启步骤

| 步骤 | 说明 |
|---|---|
| 1. 在 KernelSU / Magisk 里给本模块授权 root | **必须**。授权后设置页的「定时唤醒」才不再报缺 root |
| 2. 设置 → 定时唤醒 → 打开「定时打开抖音」 | 点「修改时间」用滚轮选钟点，不用手输 |
| 3. 精确闹钟（可选） | 不给也能用，只是可能晚十几分钟 |
| 4. 电池优化设为「不限制」 | 部分 ROM（MIUI/EMUI）会压掉闹钟 |

### 6.3 权限对照

| 权限 | 作用 | 缺了会怎样 |
|---|---|---|
| root（KernelSU / Magisk） | 用 `monkey` 拉起抖音 | **闹钟响了抖音也起不来** |
| `SCHEDULE_EXACT_ALARM` | 精确定时 | 退化成 `setAndAllowWhileIdle`，可能晚十几分钟 |
| `RECEIVE_BOOT_COMPLETED` | 重启后重排闹钟 | 关机一次就再也不响 |
| `WAKE_LOCK` | 等发送期间撑住进程 | 抖音还在冷启动时模块进程就被回收 |

本模块**不申请** `INTERNET`（头像/贴纸图由宿主从抖音缓存读出来后经 ContentProvider 推送过来）和 `SYSTEM_ALERT_WINDOW`（定时唤醒走 root + `monkey`，悬浮引导是 `PopupWindow`，都不需要该权限）。打开设置页时会在后台跑一次安装包自校验（签名证书 + 内嵌文档/字体指纹），结果写进模块日志（`自校验：签名 OK` / `资源指纹 OK`）；不匹配同样只记日志，不影响使用。

### 6.4 注意

- **全程不亮屏**：`monkey` 拉起时 `mWakefulness` 保持 `Asleep`，屏幕不会亮。
- 发完会 `am force-stop` 关掉抖音，不留后台。
- 手机重启后闹钟自动重排；覆盖安装（`MY_PACKAGE_REPLACED`）同样会重排。
- 精确闹钟是一次性的，响完 `AlarmReceiver` 立刻排下一天。
- **手动「一键清理」/「上滑清理」会连闹钟一起清掉**（`force-stop` 清空该 uid 全部 AlarmManager 闹钟）。这种情况下需要手动打开一次本模块 App 恢复。「最近任务」里不显示本模块卡片（`excludeFromRecents`），所以不会误上滑清掉它。
- 不想用定时唤醒也没关系：抖音被你自己打开时同样会跑一轮（受「只在设定时间检查」开关控制）。

---

## 七、模板、变量与保留

### 7.1 模板、继承与克隆

| 操作 | `inheritFrom` | `body` | 父模板改了 |
|---|---|---|---|
| **选会话继承** | 指向父模板 | 空（用父的） | **可同步**（开「自动同步」时） |
| **克隆到会话** | `builtin:blank` | **复制一份** | **不受影响** |

一句话：**继承 = 订阅，克隆 = 拍照**。

### 7.2 变量（两层）

| 层 | 定义位置 | 覆盖规则 |
|---|---|---|
| 模板层 | 模板编辑页的「变量」 | 所有派生会话共享 |
| 会话层 | 会话编辑页的「变量」 | 同名**覆盖**模板层 |

值是 **JS 表达式**，能读完整 `ctx`：

```js
ctx.io.days > 100 ? "老朋友" : "朋友"
```

取值语法（中文名必须用方括号）：

```js
ctx.template.vars["称呼"]
```

### 7.3 保留策略

- 在「未燃」里的会话：模板一直留着
- 移出「未燃」的：模板与发送记录**保留 30 天**后清理
- 快照：30 天没出现才会删

---

## 八、脚本 API

### 8.1 输出：`ctx.result`

引擎执行脚本前把它初始化成**空列表**，逐行 `push`，最后按行拼成**一条消息**。

```js
ctx.result.push("早安");
ctx.result.push("今天也要加油");
// 对方收到：早安\n今天也要加油
```

**空列表 = 这条不发**（回执 `Skipped`）。

### 8.2 分条发送：`ctx.commit()` / `ctx.submit()` / `ctx.exit()`

本版新增。默认一个会话只发**一条**消息；想拆成多条就调 `ctx.commit()`：

```js
ctx.result.push("早，今天也续上");
ctx.commit();              // 到这儿收成第 1 条
ctx.result.push("火花别断");
ctx.commit();              // 第 2 条
// 脚本结束时剩下的内容自动收成最后一条
```

| 方法 | 作用 |
|---|---|
| `ctx.commit()` | 把当前 `ctx.result` 收成一段（一条消息），返回 `{handle:"seg-1", index:1}` |
| `ctx.submit()` | 收尾并**立刻结束脚本**（后面的代码不再执行） |
| `ctx.exit()` | 放弃这一轮，**什么都不发** |
| `ctx.segment(handle)` | 查某一段的结果：`{handle, status:"sent"/"failed"}` |

注意：

- **一个会话最多 3 段**（含贴纸段）。超出后新内容会并进最后一条文本，避免刷屏。
- 分条发送的消息之间没有延迟控制，抖音端按顺序收到。
- 回调里的 `result` 参数多一个 `segments` 数组（`[{handle, kind, ok, name, reason}]`），可以逐段判断。

### 8.3 互动表情

本版新增。两种写法等价，推荐对象写法（表情目录随抖音版本自动更新）：

```js
// 推荐：对象写法
ctx.result.push({ type: "sticker", name: "续火花" });

// 兼容：老标记写法（v2.2.0 之前只有这一种）
ctx.result.push("[STICKER:续火花]");
```

贴纸对象只有 `name` 一个字段必填。它**自己占一条消息**：push 之前攒的文本会先收成一段，贴纸紧跟着单独发出去。

```js
ctx.result.push("给你个");
ctx.result.push({ type: "sticker", name: "续火花" });
ctx.result.push("记得回我");
// 对方收到 3 条：给你个 / 火焰贴纸 / 记得回我
```

**表情目录**（本版新增，从抖音自己的配置里读，21 个表情）：

```js
ctx.sticker.list()                    // [{name, static, animate}, ...]
ctx.sticker.list(function (it) {      // 传函数自行筛选
  return it.name.length <= 3;
});
ctx.sticker.get("续火花")              // 精确取一个，找不到返回 null
sticker("续火花")                      // 全局简写，等价于 ctx.sticker.get
```

目录为空（读不到抖音配置）时退回内置的「续火花」，此时 `ctx.sticker.list()` 只返回一项。

实现路径：

```
Message$Builder.build()
  → conversation(conversationId)
  → msgType(5)
  → content(stickerContentJson)
  → 回填 contentObj / contentUpdateFrom
  → IMessageUtils.sendMessage(msg, emptyMap, listener)
```

注意：互动表情**不走** `IMessageUtils.sendMessage` 的文本重载。抓 hook 时挂在 `Message$Builder.build()` 上才抓得到。

表情资源 URL 带签名，有效期到 **2027-11**。本版起优先用抖音当前下发的地址（签名更新），只有读不到配置时才用内置地址。

### 8.4 变量

| 对象 | 内容 |
|---|---|
| `ctx.io.id` | 会话 ID |
| `ctx.io.nickname` / `remark` / `name` | 昵称 / 备注 / 显示名（备注优先） |
| `ctx.io.days` | 已连续聊天天数 |
| `ctx.io.state` | `1`=今天已互动 `2`=今天没互动 `3`=待重燃 `-1`=无火花 |
| `ctx.io.stateText` | 状态文本（如「重燃中」） |
| `ctx.io.peerUid` | 对方 UID |
| `ctx.template.vars["名字"]` | 模板 / 会话变量 |
| `ctx.template.template` | 当前模板 `{id, name}` |
| `ctx.template.parent` | 父模板 `{id, name}` |

```js
var who = ctx.io.name || "朋友";
ctx.result.push(who + "，火花 " + ctx.io.days + " 天啦");
```

### 8.5 `ctx.builtin`

| 函数 | 返回 |
|---|---|
| `date` / `time` / `weekday` / `timestamp` | 当天日期 / 当前时间 / 星期几 / 毫秒时间戳 |
| `pick(list)` | 从数组随机取一个 |
| `pad(n)` | 补零到两位 |
| `repeat(s, n)` | 重复字符串 |
| `random(a, b)` | `[a, b)` 随机整数 |
| `shuffle(list)` | 洗牌（返回新数组） |
| `countdown("2027-01-01")` | 距目标日期还有几天 |
| `nextNewYear()` / `untilNewYear()` | 下一个元旦日期 / 还有几天 |
| `formatDate(date, "yyyy/MM/dd")` | 按模式格式化 |

### 8.6 `ctx.system`

| 方法 | 说明 |
|---|---|
| `getAllConversation(filter?)` | 本轮可见的会话数组 |
| `getConversation(id)` | 按 id 取会话详情 |
| `getTemplate(id)` | 取模板 `{id, name, body, childs}` |

筛选示例：

```js
var targets = ctx.system.getAllConversation({ willSend: true });
var hot = ctx.system.getAllConversation({ state: [1, 2] });
var longSpark = ctx.system.getAllConversation({ days: function (d) { return d >= 100; } });
```

---

## 九、编辑器

模板 / 会话文案用的是**普通文本框**（等宽字体、多行）。

底部动作条：

| 按钮 | 作用 |
|---|---|
| 预览 | 用真实会话数据试算 |
| 粘贴 | 光标处粘贴剪贴板内容 |
| 格式化 | 清掉零宽字符、智能引号转直引号、去掉 Markdown 代码围栏 |

没有语法高亮和自动补全（上游用的 WebView + CodeMirror，本版为了省 1.2 MB 去掉了）。

---

## 十、用 AI 写脚本

把下面这段整段贴给 AI，它就能按本文档的 API 生成脚本。

```
你是「抖音续火花cc」抖音自动续火花模块的文案脚本作者。只输出 JavaScript 代码，不要解释。

可用能力（全部挂在 ctx 上）：
ctx.result.push("一行内容")        输出，空列表 = 这条不发；多行拼成一条消息
ctx.commit()                       把当前内容收成一条，之后接着攒下一条（每会话最多 3 条）
ctx.submit()                       收尾并立刻结束脚本
ctx.exit()                         放弃这一轮，什么都不发
ctx.result.push({type:"sticker", name:"续火花"})  发互动表情（火焰贴纸）
ctx.sticker.list()                 当前可用的互动表情名字（21 个）
ctx.sticker.get("续火花")           精确取一个贴纸对象
ctx.io.name / nickname / remark    显示名 / 昵称 / 备注
ctx.io.days                        连续聊天天数
ctx.io.state                       1=今天已互动 2=今天没互动 3=待重燃 -1=无火花
ctx.io.stateText                   状态文本
ctx.template.vars["名字"]           模板 / 会话变量（中文必须方括号）
ctx.builtin.date / time / weekday  日期 / 时间 / 星期
ctx.builtin.pick(list)             随机取一个
ctx.builtin.random(a, b)           [a,b) 随机整数
ctx.builtin.shuffle(list)          洗牌
ctx.builtin.countdown("2027-01-01") 距目标日期天数
ctx.builtin.formatDate(date, "yyyy/MM/dd")

写法要求：
- 只用 ctx.* 和 Math / JSON / Date / Array / String，没有网络和 DOM
- ctx.io.name / days 都要兜底（不会输出 undefined）
- 不要每天一模一样：用 ctx.builtin.pick([...]) 准备 3 个以上备选
```

### 写作建议

| 要点 | 说明 |
|---|---|
| 多准备备选 | 一个池子 3~5 句，用 `pick` 抽，避免天天一样 |
| 能算的别写死 | 天数用 `ctx.io.days`、日期用 `ctx.builtin.date` |
| 兜底 | `ctx.io.name` 可能为空、`ctx.io.days` 可能是 0 |
| 短 | 一句话最好，长了像营销号 |

### 示例

```js
var who = ctx.io.name || "朋友";
ctx.result.push(ctx.builtin.pick([
  who + "，今天也来续一下火花吧",
  "火花 " + ctx.io.days + " 天啦，" + who + " 别断～",
  ctx.builtin.time + " 了，" + who + " 记得回我一下"
]));
```

---

## 十一、常见问题

**Q：点发送提示「没有可发送的会话」？**
先去「未燃」页勾几个，或等抖音扫描出待续会话。

**Q：某个会话一直不出现？**
它可能被隐藏了（连续 3 次没在抖音出现）。去「全部」页底部的「被隐藏」卡片点「恢复」。

**Q：发了没反应 / 抖音里没看到消息？**
1. 确认抖音在运行
2. 「设置 → 宿主状态」有没有内容
3. 改完 APK 后必须重启抖音

**Q：「今日已发」是什么意思？**
今天我们发过这条了。**不代表对方回了**。

**Q：「重燃中 2/3」是什么意思？**
火花断了，需要连续互发 3 天才能恢复，已完成 2 天。

**Q：「6 天后消失」？**
再不动手，火花彻底没了。

**Q：给没火花的人发消息怎么弄？**
「全部」页底部「无火花」卡片 → 对某人点「移入未燃」→ 之后每天自动发。
注意：抖音对给陌生人批量发相似内容比较敏感，建议先试 2~3 个。

**Q：群聊会发吗？**
会，和单聊一个待遇。

**Q：改了模板但会话没变？**
那个会话是**克隆**来的（不跟随），或者继承但没开「自动同步」。

**Q：配置会不会丢？**
覆盖安装（同签名）不会丢。**换签名或卸载会丢**，请先在「设置 → 导出配置」备份。
配置文件损坏时，模块会另存为 `spark_config.json.broken-<时间戳>` 并拒绝覆盖。

**Q：能定时自动发吗？**
能，但需要 root。设置 → 定时唤醒 → 打开「定时打开抖音」，并在 KernelSU / Magisk 里给本模块授权。详见第六节。

**Q：锁屏能发吗？**
能。发送不需要亮屏也不需要解锁。前提是抖音进程已经起来 —— 靠定时唤醒或你自己打开抖音触发。

**Q：闹钟到点了但抖音没起来？**
三件事依次查：①「设置 → 定时唤醒」是否显示「未获得 root 授权」—— 在 KernelSU / Magisk 里给本模块授权即可；② 是否刚做过一键清理 / 上滑清理（会把闹钟一起清掉，手动开一次本模块 App 恢复）；③ 是否装了两个同包名版本互相覆盖。

---

## 十二、数据与文件

| 内容 | 路径 |
|---|---|
| 配置 | `/data/data/io.github.tiantong007.douyinspark/files/spark_config.json` |
| 损坏备份 | 同目录 `spark_config.json.broken-<时间戳>` |
| 发送记录 | 配置里的 `sentDays` 字段 |

模块**不联网上报任何数据**。会话列表、火花状态、发送记录都只存在本机。

---

## 附录 A：一次发送的完整时序

```
定时唤醒（AlarmManager 到点 → root monkey 拉起抖音，不亮屏）
  └─ 抖音进程启动 → Application.attach 被 hook
       └─ scheduleOnAppStart（延迟 5 秒，等 IM 就绪，最多重试 22 次 ≈ 60 秒）

抖音启动 / 回前台 / 跨天
  └─ 模块扫描会话列表
       └─ 每个会话提取火花数据（state / days / flameText / expireText）
            └─ 合并进快照（missCount 计数，连续 3 次没出现就隐藏）
                 └─ 筛出候选：未燃里的会话
                      └─ 对每个候选：
                           1. 取模板（会话自己的 > 继承的父模板）
                           2. 注入变量（模板层 + 会话层）
                           3. 执行脚本 → ctx.result（ctx.commit 切段）
                           4. 空列表 → Skipped
                           5. 逐段发：贴纸段 → sendStickerJson（msgType=5）
                              文本段 → sendTextJson（msgType=7）
                           6. 等发送回执 → Succeed / Failed
                           7. 成功则记 sentDays
```

发送完成后的关闭（由 AlarmReceiver 侧做，不在抖音进程里）
  └─ 每 10 秒读 /data/data/com.ss.android.ugc.aweme/files/spark_last_auto_day
       └─ 变成今天 → 再等 3 秒让 markSent 落盘 → am force-stop 关掉抖音
            └─ 180 秒还没变 → 放弃本轮，不动进程

---

## 附录 B：构建

需要 **JDK 21**、Android SDK 36、build-tools 36.0.0。

```powershell
$env:JAVA_HOME="<你的 JDK 21 路径>"
cd src
./gradlew.bat :app:assembleRelease --no-daemon
# 产物：app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk
```

或直接用仓库根的 `build.ps1`（含 zipalign + 签名）：

```powershell
./build.ps1
```

`src/local.properties` 需指向你的 SDK（不在版本控制里）：

```properties
sdk.dir=C\:\\path\\to\\android-sdk
```

### 目录

```
README.md        本文件，也是 App 内「使用文档」的源（构建时同步进 assets）
docs/            状态快照 / 构建签名 / 表情协议（含抓包样本）/ 已知坑 / 发布流程
src/             源码
keystore/        签名密钥（.gitignore 排除，不在仓库里）
release/         构建产物
tools/           frida 启停脚本（调试抓包用）
build.ps1        一键编译 + 对齐 + 签名
```

### 版本号规则

v2.0.0 起启用独立版本号，不再沿用上游 `v1.5.x-ccN` 序列（功能集已分叉）。

```
versionCode = 主*10000 + 次*100 + 修订 + 构建序号
              例：v2.0.0 第 1 次构建 → 20001
                  v2.0.0 第 2 次构建 → 20002
                  v2.0.1            → 20101
tag         = <versionCode>-<versionName> → 20001-v2.0.0
```

`versionCode` 必须单调递增，否则降级安装失败。

---

## 附录 C：来源与许可

本仓库基于 [WorldMargin/SparkFlame](https://github.com/WorldMargin/SparkFlame) v1.5.7（Apache-2.0）改造，功能已分叉，包名与签名均独立，两者不能互相覆盖。

上游代码与思路的致谢：

- **[yfishyon/doumcp](https://github.com/yfishyon/doumcp)** —— 抖音逆向与注入能力的基础（会话、IM 发送、火花字段等）
- **WorldMargin** —— 模板 / 变量体系，以及 DouYinSpark 油猴脚本原作
- **DeepSeek** —— 把 DouYinSpark 油猴脚本逻辑迁移至安卓

Apache-2.0，保留原作者版权声明，见 [LICENSE](LICENSE)。