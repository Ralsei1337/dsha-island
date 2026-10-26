# 免 root 能力边界

> 结论基于 2026-10-27 的公开 API、HyperOS 真机测试，以及 OPPO/vivo/华为的官方文档。
> ColorOS/vivo/Huawei 没有在本项目真机上验证，结论会标注“公开 API / 未上机”。

## 目录

1. [免 root 的链路](#1-免-root-的链路)
2. [能力矩阵](#2-能力矩阵)
3. [Xiaomi HyperOS 实测](#3-xiaomi-hyperos-实测)
4. [ColorOS 16](#4-coloros-16)
5. [OriginOS](#5-originos)
6. [HarmonyOS](#6-harmonyos)
7. [明确做不到的事](#7-明确做不到的事)
8. [把免 root 做到最好](#8-把免-root-做到最好)
9. [App 内能力报告](#9-app-内能力报告)

---

## 1. 免 root 的链路

```text
DSHA 插件（读 session/event）
  → 127.0.0.1:8798
  → Lite（声明 POST_PROMOTED_NOTIFICATIONS）
  → Notification.Builder:
       setOngoing(true)
       setShortCriticalText(...)
       setRequestPromotedOngoing(true)
       setColorized(true)
       setProgress(...)
  → Android 16 Live Updates
  → 系统决定渲染成：胶囊 / 卡片 / 普通通知
```

免 root 只能走**公开 Notification API**。系统最终渲染成什么样，由 OEM 决定。

---

## 2. 能力矩阵

| 能力 | HyperOS 无 root | ColorOS 16 无 root | OriginOS 无 root | HarmonyOS 无 root |
|---|---|---|---|---|
| 状态栏胶囊 | 有（AOSP promoted） | 有 | 取决于系统 | 普通通知 |
| 展开大卡片 | 无官方大岛 | 有 Live Update 卡片（公开 API） | 取决于系统 | 无 |
| 显示路径/命令 | 可以（Lite 正文） | 可以（Lite 正文） | 可以（若上岛） | 仅通知栏 |
| 当前动作耗时 | 可以（Lite 自己计时） | 可以 | 可以 | 可以 |
| 任务总耗时 | 可以（插件给总时长） | 可以 | 可以 | 可以 |
| 岛上「停止」按钮 | **不行** | **不行** | **不行** | **不行** |
| 官方大岛样式/动效 | **不行** | 不是 OPPO 私有卡 | **不行** | **不行** |
| 控制 DSHA 停止 | **不行** | **不行** | **不行** | **不行** |
| 读其他 App 通知 | 需通知使用权，本方案不用 | 同左 | 同左 | 同左 |

> “可以”指 Lite 能把文字/进度发到系统；系统是否显示成对应样式，仍取决于 OEM。

---

## 3. Xiaomi HyperOS 实测

测试机：Xiaomi 25113PN0EC，HyperOS 4.0.2，Android 17。

无 root（Lite + 插件）：

- 状态栏出现 **AOSP 胶囊**；
- 胶囊文字能显示 `写入 /path`、`执行 cmd`、`清单 1/2`；
- 展开只有普通通知样式，**没有** HyperOS 官方大岛的 `baseInfo` / `hintInfo` / 按钮；
- 没有岛上「停止」；
- 通知被系统清理/省电限制后，胶囊消失；
- Lite 进程被停后，插件推送无人接收。

有 root（模块 + 插件）：

- 官方 `miui.focus.param` 大岛；
- A 区动作+路径，B 区当前动作耗时，卡片任务总耗时；
- 岛上「停止」；
- 但模块必须只对 HyperOS 生效，其他品牌 inactive。

**结论：HyperOS 的免 root 上限就是 AOSP 胶囊，官方大岛拿不到。**

---

## 4. ColorOS 16

OPPO 官方说法：ColorOS 16 的流体云完整接入 Android 16 Live Updates，应用遵循 Google 实时活动规范即可适配。

免 root 能做的：

- Lite 已声明 `POST_PROMOTED_NOTIFICATIONS`；
- 发 promoted + shortCriticalText + colorized + progress；
- ColorOS 16 可渲染成流体云 Live Update 卡片；
- 不需要 root，也不需要 OPPO UPK/白名单（走 AOSP 通道）；
- 但卡片模板由系统决定，不能做 OPPO 私有大卡/自定义组件。

未上机验证：本项目没有 OPPO 设备，结论来自 OPPO 公开兼容说明 + AOSP 规范。

---

## 5. OriginOS

vivo 的公开接入是「原子通知」，属于厂商能力；没有公开承诺 AOSP Live Updates 一定能上原子通知。

免 root 能做的：

- 如果 OriginOS 版本实现了 Android 16 Live Updates：Lite 的 promoted 通知可能被渲染成原子通知；
- 如果没有实现：只会是普通通知；
- 不能调用 vivo 私有原子通知 SDK（需要开发者资质/白名单）；
- 不能保证卡片样式。

未上机验证：没有 vivo 设备。

---

## 6. HarmonyOS

- HarmonyOS 4（Android 底）：Android APK 不能使用实况窗；Lite 只能发普通通知。
- HarmonyOS NEXT：不能安装 APK，本方案不适用。
- 华为实况窗需要鸿蒙应用 + 华为能力接入，不是 AOSP 通知能触发的。

---

## 7. 明确做不到的事

1. **官方大岛/官方流体云私有卡**：需要系统签名、厂商 SDK 或系统级 hook；
2. **岛上停止按钮**：Lite 没有 DSHA 的停止接口，不能代替用户点 DSHA；
3. **控制 DSHA 前台/后台**：免 root 不能强制 DSHA 保持后台；
4. **绕过电池优化/后台限制**：只能引导用户设“无限制”；
5. **读取其他 App 通知**：需要通知使用权；本方案靠 DSHA 插件，不碰其他 App；
6. **保证 OEM 一定渲染**：最终样式由系统决定，系统升级可能变。

---

## 8. 把免 root 做到最好

1. 通知权限、`POST_PROMOTED_NOTIFICATIONS` 都要给；
2. 电池策略设为“无限制”，允许自启动；
3. Android 16+ 才有 Live Updates；
4. 用 Lite 的 `检查更新` 保持最新版；
5. 看 App 内“免 root 能力”报告，确认 `Live Updates 可用`；
6. ColorOS 16 优先用 Lite，不需要 root；
7. HyperOS 想要官方大岛/停止按钮，只能 root + 模块。

---

## 9. App 内能力报告

Lite 状态页会显示：

```text
Xiaomi 25113PN0EC · Android 17 · 超级岛 · Live Updates 可用
免 root 能力：AOSP 胶囊（官方大岛/岛上停止需 root）
```

或：

```text
HUAWEI ... · Android 12 · 实况窗 · 普通通知降级
免 root 能力：普通通知（系统未开放 Live Updates）
```

`检查更新` 按钮只在你点击时请求 GitHub API：

```text
https://api.github.com/repos/Ralsei1337/dsha-island/releases/latest
```

不后台轮询、不自动下载；有新版本时给 Release 链接。
