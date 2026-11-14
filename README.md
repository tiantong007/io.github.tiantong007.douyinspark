# 抖音续火花cc

抖音「自动续火花」LSPosed 模块，基于 [WorldMargin/SparkFlame](https://github.com/WorldMargin/SparkFlame) v1.5.7 改造。

> 本仓库为个人修改版，与上游作者无关。遵循 Apache-2.0，见 `LICENSE`。

## 与原版的区别

| 项 | 官方 v1.5.7 | 本版 |
|---|---|---|
| 包名 | `io.github.worldmargin.sparkflame` | `io.github.tiantong007.douyinspark` |
| 签名 | 作者私钥 | `CN=tiantong007` |
| 名称 | 星火燎原 | **抖音续火花cc** |
| 互动表情 | ❌ 只能发文本表情码 | ✅ **可发「续火花」火焰互动表情**（msgType=5） |
| 群聊 | 默认不参与自动续火花 | 可参与（`AutoSparkBridge:929` 待改） |
| 标签功能 | 有 | （精简中） |

## 功能

- **自动续火花**：抖音启动 / 回前台 / 跨天时扫一轮，给「今天还没互动」的会话发一条
- **模板**：内置 7 个 + 自定义，支持继承 / 克隆 / 派生
- **两层变量**：模板层 + 会话层，值是 JS 表达式
- **脚本引擎**：`ctx.result` / `ctx.io` / `ctx.builtin` / `ctx.template` / `ctx.system` / `ctx.macro` / `ctx.functions`
- **互动表情**：`ctx.result.push("[STICKER:续火花]")` → 发火焰贴纸（本版私有）
- **防重发**：每会话每天最多一条（`sentDays` 台账）
- **三态回执**：`Succeed` / `Skipped` / `Failed`

## 要求

- Android 9+，已 Root
- LSPosed **2.x**（libxposed API 101+，`module.prop` 声明 `minApiVersion=101`）
- 作用域：`com.ss.android.ugc.aweme`

## 安装

1. 装 APK
2. LSPosed 里启用模块，勾选「抖音」（`staticScope=true`，通常自动勾好）
3. 强制停止抖音再打开
4. 打开模块 App 一次，确认「设置 → 宿主状态」有内容

> ⚠️ 本版签名与官方「星火燎原」不同，**两者不能互相覆盖**，同时装需先卸载其中一个。

## 构建

```powershell
.\build.ps1 -Arm64Only
```

需要 JDK 21 + Android SDK 36 + build-tools 36.0.0，详见 `docs\构建与签名.md`。

## 目录

```
src\        SparkFlame 源码（上游 v1.5.7 + 本版改动）
docs\       状态快照 / 改动清单 / 构建签名 / 表情协议 / 已知坑 / 发布流程
keystore\   签名密钥（不进 git）
release\    构建产物
tools\      frida 启停脚本
build.ps1   一键编译+签名
```

## 私有约定

`[STICKER:表情名]` 是本版新增的模板标记，只有本项目版本识别。写进模板后如果退回官方版，会被当普通文字发出去。

## 许可证

Apache-2.0。基于 WorldMargin/SparkFlame 修改，保留原作者版权声明。
