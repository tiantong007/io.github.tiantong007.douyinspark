# 抖音续火花cc

抖音「自动续火花」LSPosed 模块 —— 基于 [WorldMargin/SparkFlame](https://github.com/WorldMargin/SparkFlame) v1.5.7 改造的个人版。

**一句话**：给「抖音判定今天还没互动」的会话，自动发一条你自己写的私信，每个会话每天最多一条。

> 本仓库为个人修改版，与上游作者无关。遵循 Apache-2.0，见 [LICENSE](LICENSE)。

---

## 与官方版的差异

| 项 | 官方 SparkFlame v1.5.7 | 本版 |
|---|---|---|
| 包名 | `io.github.worldmargin.sparkflame` | `io.github.tiantong007.douyinspark` |
| 签名 | 作者私钥 | 独立签名（**两者不能互相覆盖**） |
| 名称 | 星火燎原 | 抖音续火花cc |
| **互动表情** | ❌ 只能发文本表情码 | ✅ **可发「续火花」火焰贴纸**（msgType=5） |
| 续火花页 | 已选目标 / 未选目标 | **未燃 / 已燃 / 全部** 三页 |
| 群聊 | 默认不参与 | 与单聊同等参与 |
| 内置模板 | 6 个 | **2 个**（续火花表情 / 通用问候） |
| 标签 | ✅ | ❌ 移除 |
| 宏 / 函数界面 | ✅ | ❌ 移除（引擎保留，老配置兼容） |
| 编辑器 | WebView + CodeMirror | 原生文本框 |
| APK | 2.0 MB | **0.78 MB** |

---

## 功能

### 续火花
- **三页视图**：未燃（该续的）/ 已燃（今天已互动）/ 全部（有火花的）
- **无火花卡片**：全部页底部，可把没火花的会话手动移入未燃，之后每天自动发
- **被隐藏卡片**：连续 3 次没在抖音出现的会话收在这里，可恢复
- **批量操作**：勾选后移入/移出，按钮跟着页面变
- **状态可见**：`火花 410 天 · 重燃中 2/3 · 今日已发 · 6 天后消失`

### 模板
- 2 个内置：**续火花表情**（发火焰贴纸）、**通用问候**（两句随机）
- 自定义模板，支持**继承**（跟随父模板）与**克隆**（复制后独立）
- 两层变量（模板层 + 会话层），值是 JS 表达式

### 脚本
```js
var who = ctx.io.name || "朋友";
ctx.result.push("[STICKER:续火花]");          // 发互动表情（本版私有）
ctx.result.push(who + "，火花 " + ctx.io.days + " 天啦");
```

完整 API 见 App 内「使用文档」或 [`assets/docs/manual.md`](src/app/src/main/assets/docs/manual.md)。

### 可靠性
- 每会话每天最多一条（`sentDays` 台账）
- 配置损坏时**自动备份并拒绝覆盖**（`spark_config.json.broken-*`）
- 三态回执：Succeed / Skipped / Failed

---

## 要求

- Android 9+
- 已 Root
- **LSPosed 2.x**（libxposed API 101+）
- 作用域：`com.ss.android.ugc.aweme`
- **仅 arm64-v8a**

⚠️ 本版签名与官方「星火燎原」不同，**两者同时安装会并存但作用域重复**，建议只留一个。

---

## 安装

1. 安装 APK
2. LSPosed 里启用模块，勾选「抖音」（`staticScope=true`，通常已自动勾好）
3. **强制停止抖音再打开**
4. 打开模块 App 一次，确认「设置 → 宿主状态」有内容

升级时：直接覆盖安装（同签名），配置保留。**换签名或卸载会丢配置**，请先在「设置 → 导出配置」备份。

---

## 使用要点

| 场景 | 怎么做 |
|---|---|
| 只想发文字 | 模板写 `ctx.result.push("...")` |
| 想发续火花火焰 | 模板写 `ctx.result.push("[STICKER:续火花]")` |
| 想给没火花的人发 | 全部页底部「无火花」→ 某人「移入未燃」 |
| 不想给某人发 | 未燃页勾选 → 「移出未燃」 |
| 改了模板但会话没变 | 检查它是克隆来的，还是继承但没开自动同步 |

---

## 构建

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

---

## 目录

```
src/            SparkFlame 源码（上游 v1.5.7 + 本版改动）
docs/           状态快照 / 改动清单 / 构建签名 / 表情协议 / 已知坑 / 发布流程
docs/改动.diff  与上游 v1.5.7 的完整差异
keystore/       签名密钥（.gitignore 排除，不在仓库里）
release/        构建产物
tools/          frida 启停脚本（调试抓包用）
build.ps1       一键编译 + 对齐 + 签名
```

---

## 已知限制

- **无补全**：编辑器是原生文本框，没有 `ctx.*` 自动补全
- **互动表情是私有标记**：`[STICKER:xxx]` 只有本版识别，退回官方版会当文字发出去
- **表情 url 会过期**：内置的续火花表情资源签名有效期到 2027-11，过期需重新抓取
- **仅 arm64**：32 位机型装不了
- **抖音升级可能失效**：模块依赖的混淆类名会变

---

## 数据与隐私

模块**不联网上报任何数据**。会话列表、火花状态、发送记录都只存在本机：

```
/data/data/io.github.tiantong007.douyinspark/files/spark_config.json
```

---

## 基于

上游项目：[WorldMargin/SparkFlame](https://github.com/WorldMargin/SparkFlame)（Apache-2.0）

本版改动明细见 [`docs/改动清单.md`](docs/改动清单.md)。

## 许可证

Apache-2.0，保留原作者版权声明，见 [LICENSE](LICENSE)。
