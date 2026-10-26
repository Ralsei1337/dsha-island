# DSHA Island

## 下载

[v0.4.1 Release](https://github.com/Ralsei1337/dsha-island/releases/tag/v0.4.1)

| 文件 | 用途 |
|---|---|
| `dsha-island-0.4.1.apk` | root + LSPosed，完整大岛 |
| `dsha-island-lite-0.4.1.apk` | 免 root 伴生 App |
| `dsh-island-lite-0.4.1.tar.gz` | DSHA 插件（root 直读 8797 / Lite 推送） |
| `SHA256SUMS.txt` | 校验和 |


> 独立的 HyperOS 4 超级岛适配模块，**不修改 DSHA、不依赖 WorkIsland、不读取通知内容以外的数据**。
> 状态：v0.4.1（已真机验证官方 param 生效；旧版 FGS 通知会抢岛，0.1.2 已按频道过滤）

---

## 目录

- [下载](#下载)
- [**详细使用教程**](docs/使用教程.md) ← 第一次用先看这个
- [免 root 能力边界](docs/免root能力边界.md)
- [1. 它是什么](#1-它是什么)
- [1.1 两种形态](#11-两种形态)
- [1.2 平台支持](#12-平台支持)
- [2. 它不做什么](#2-它不做什么)
- [3. 架构](#3-架构)
- [4. 文件结构](#4-文件结构)
- [5. 构建](#5-构建)
- [6. 安装](#6-安装)
- [7. 验证](#7-验证)
- [8. 状态映射](#8-状态映射)
- [9. 权限解锁的安全边界](#9-权限解锁的安全边界)
- [11. 卸载 / 回滚](#11-卸载--回滚)
- [12. 已知限制](#12-已知限制)
- [13. 更新日志](#13-更新日志)
- [CHANGELOG.md](CHANGELOG.md)
- [从源码构建教程](docs/使用教程.md#9-从源码构建)

## 1. 它是什么

一个只作用于 `com.android.systemui` 的 LSPosed 模块：

- 识别 DSHA（`dsh.android.client`）自己发出的任务通知；
- 在通知上补 HyperOS 官方 `miui.focus.param`（大岛 / 小岛 / 焦点卡 / 按钮）；
- 打开 AOSP promoted ongoing + `shortCriticalText`（无官方权限时的保底胶囊）；
- 只对 DSHA 放开焦点通知校验（`canShowFocus` / `canCustomFocus` / `SignatureChecker`），不全局解锁。

**不改 DSHA 一行代码**，DSHA 升级通常不需要改模块；最坏情况退化成普通通知。

---

## 1.1 两种形态

| 形态 | 目录 | 需要 root | 岛上能力 | 适用 |
|---|---|---|---|---|
| **LSPosed 模块** | 根目录 | 是 | 官方大岛/小岛/文字停止按钮 | 个人设备，已 root |
| **DSHA Island Lite** | `lite/` + `plugin/` | 否 | AOSP 胶囊：图标 + 一行文字 | 免 root、可分发 |

两者可以共存，但**同一轮任务建议只启用一个**，否则两条通知会抢同一个岛位。

## 1.2 平台支持

| 系统 | 走哪条路 | 岛形态 | 状态 |
|---|---|---|---|
| Xiaomi HyperOS 3/4 | root 模块 / Lite | 超级岛：官方大岛或胶囊 | 真机已验证 |
| OPPO / 一加 / realme ColorOS 16（Android 16） | Lite promoted | 流体云 / Live Updates | 公开 API 兼容；本机未测试 |
| vivo / iQOO OriginOS（Android 16） | Lite promoted | 原子通知（若系统实现 Live Updates） | 尽力适配；私有原子通知 SDK 未使用 |
| Huawei HarmonyOS 4（Android 底） | Lite 普通通知 | 无（Android APK 不能用实况窗） | 仅通知栏 |
| Huawei HarmonyOS NEXT | 不支持 | — | 不能安装 APK |
| Honor MagicOS | Lite promoted | 灵动胶囊（若系统实现） | 尽力适配；未测试 |
| 其他 Android 16 | Lite promoted | 实时通知 | 尽力适配 |

- root LSPosed 模块只对小米/HyperOS 生效；其他品牌安装后自动 inactive。
- OPPO/vivo/华为的厂商私有 SDK（流体云 UPK、原子通知、实况窗）需要开发者资质/白名单，本项目不包含，也不伪造。

## 2. 它不做什么

- 不代理、不伪造其他应用的通知；
- 不读取通知里的文件路径、命令、模型输出以外的内容；
- 不做岛内“批准/拒绝”工具调用（DSHA 的审批绑定 Activity，后台会 cancel；岛上只提供“打开应用”）；
- 不申请 AppId，不冒充小米官方授权；
- 不需要 root 权限本身，只需要 LSPosed（LSPosed 已需要 root）。

---

## 3. 架构

```
DSHA 进程
  AgentTaskNotifications.render()  ──1 Hz──▶ NotificationManager.notify()
                                                    │
                                                    ▼
SystemUI 进程（模块作用域）
  MiuiBaseNotifUtil.generateInnerNotifBean(sbn)   ◀── 模块在这里改 extras
      │  1) 只认 dsh.android.client
      │  2) 从通知正文推断阶段（等待/执行/思考/完成/停止）
      │  3) 写 miui.focus.param + miui.focus.pics + miui.focus.actions
      │  4) 写 promoted + android.shortCriticalText
      ▼
  超级岛：小岛图标 + 短文本；大岛 A/B 区；焦点卡；停止/打开按钮
      │
      └─ 按钮 PendingIntent 仍然指向 DSHA 自己的 Receiver / Activity
```

### 为什么在 SystemUI 改，而不是改 DSHA

- DSHA 通知里已经有「停止」「回聊天」两个 `Notification.Action`，模块直接复用它们的 `PendingIntent`，岛上点“停止”就是真停止；
- DSHA 升级只影响通知文案，不影响包名/作用域；
- 不需要给 DSHA 加任何 exported 组件或新权限。

---

## 4. 文件结构

```
dsha-island/
├── AndroidManifest.xml                 # 模块 App + 设置页
├── build.sh                            # aapt2 + javac + d8 + apksigner
├── libs/libxposed-api-102.jar          # compileOnly，不打进 APK
├── keys/dsha-island.jks                # 本地调试签名（勿用于正式发布）
├── module/META-INF/xposed/
│   ├── java_init.list                  # 入口类
│   ├── module.prop                     # minApi 101 / targetApi 102
│   └── scope.list                      # com.android.systemui
├── lite/                               # 免 root 伴生 App
│   ├── AndroidManifest.xml
│   ├── build.sh
│   ├── res/drawable/ic_lite.png
│   └── src/io/github/dsha/island/lite/ # MainActivity / IslandService / BootReceiver
├── plugin/                             # 免 root 的 DSHA 用户插件
│   ├── package.json
│   ├── cordis.patch.yml
│   └── lib/index.js
├── src/io/github/dsha/island/
│   ├── DshaIslandModule.java           # 入口 + hook + 解锁
│   ├── IslandPayload.java              # param_v2 / pics / actions
│   ├── Phase.java                      # 通知文案 -> 阶段
│   └── SettingsActivity.java           # 说明页（无权限、无网络）
├── README.md
├── CHANGELOG.md                         # 版本历史 / 版本策略
├── docs/使用教程.md                     # 手把手安装、验证、排错
├── LICENSE                              # MIT
└── out/current/dsha-island-0.4.1.apk    # 构建产物
```

---

## 5. 构建

本机（aarch64 Ubuntu 容器）已验证可构建：

```bash
cd dsha-island
./build.sh
# 产物：out/current/dsha-island-0.4.1.apk
```

依赖：

| 项 | 路径 |
|---|---|
| Android SDK platform | `/opt/android-sdk/platforms/android-37.0/android.jar` |
| build-tools | `/opt/android-sdk/build-tools/android-37.0` |
| aapt2（aarch64） | `/opt/termux-tools/usr/bin/aapt2`（需 `LD_LIBRARY_PATH=/opt/termux-tools/usr/lib`） |
| libxposed API | `libs/libxposed-api-102.jar`（来自 Maven `io.github.libxposed:api:102.0.0` 的 `classes.jar`） |

> 构建脚本会把工程复制到 `/tmp/dsha-island-build` 再编译：本机 JDK 的
> `sun.jnu.encoding=ANSI_X3.4-1968`，无法打开中文路径。

---

## 6. 安装

> 完整步骤、截图位置、排错见 **[使用教程](docs/使用教程.md)**。下面只列最短路径。

> 本机设备策略禁止 `adb-shell pm install`，所以安装必须手动走系统安装器。

1. 把 `out/current/dsha-island-0.4.1.apk` 传到手机（或从 `Download/DSHA/DSHA-Island/01-当前` 打开）。
2. 文件管理器点 APK → 继续安装（HyperOS 会提示“文件管理正尝试安装应用”）。
3. 打开 **LSPosed 管理器** → 模块 → 勾选 **DSHA Island**。
4. 作用域只勾 **`com.android.systemui`**（不要勾 DSHA，模块不进入 DSHA 进程）。
5. 重启手机（或 LSPosed 里的软重启）。
6. 打开 **DSHA Island** 设置页，确认说明页能打开（可选）。

---

## 7. 验证

### 7.1 看日志

```bash
adb logcat -s DshaIsland
```

期望看到：

```
SystemUI loaded, installing DSHA island hooks
hooked MiuiBaseNotifUtil.generateInnerNotifBean
hooked miui.systemui.notification.NotificationSettingsManager#canShowFocus
hooked ...#canCustomFocus
hooked miui.systemui.notification.focus.SignatureChecker#checkSignatures
enriched phase=TOOL short=执行中 42s stop=true open=true
```

如果只有 `pipeline hook missing`，说明这个 HyperOS 版本的类名/方法签名变了，需要按第 10 节排查。

### 7.2 看岛

1. DSHA 回到后台跑一个长任务；
2. 主屏顶部应出现胶囊：`DSHA 图标 + 执行中 42s`；
3. 点开 / 下拉应看到大岛 A/B 区与「停止」「打开」；
4. 点「停止」→ DSHA 任务结束 → 岛变「已停止」。

> DSHA 在前台时不会发通知（它自己 cancel），所以**测试时必须回桌面**。

### 7.3 看通知 extras

```bash
adb shell dumpsys notification --noredact    # 电脑 adb；设备 shell 里分条执行
```

查找 `dsh.android.client`，期望：

```
miui.focus.param=...
android.requestPromotedOngoing=true
android.shortCriticalText=执行中 42s
```

---

## 8. 状态映射

| DSHA 通知正文 | 岛上短文本 | 大岛 A | 大岛 B | 按钮 |
|---|---|---|---|---|
| 等待处理 / Waiting for input | 等你确认 | 等你确认 | 打开应用处理 | 打开 |
| 正在执行工具 / Running tool | 执行中 42s | 执行中 · 42s | 正在执行工具 | 停止 / 打开 |
| 正在生成回复 / Generating reply | 思考中 12s | 思考中 · 12s | 正在生成回复 | 停止 / 打开 |
| 已请求停止 / Stop requested | 正在停止 | 正在停止 | 等待结束 | 打开 |
| 任务完成 / Task completed | 任务完成 | 任务完成 · 3分12秒 | 查看结果 | 打开 |
| 任务已停止 / Task stopped | 已停止 | 已停止 | 查看结果 | 打开 |
| 已达到输出上限 / Output limit | 已达上限 | 已达上限 | 查看结果 | 打开 |
| 任务需要处理 / needs attention | 需要处理 | 需要处理 | 打开应用 | 打开 |

映射靠通知正文的**包含匹配**，中英双语都覆盖；认不出时退回 `DSHA 运行中`。

---

## 9. 权限解锁的安全边界

模块只在两种情况下让焦点校验返回 `true`：

1. 参数里直接出现 `dsh.android.client`；
2. 刚刚处理过 DSHA 通知的 **1.5 秒线程窗口 / 2 秒全局窗口**。

不会无条件 `return true`。代价是：DSHA 每秒更新通知时，2 秒窗口几乎一直开着，
这段时间内其他应用如果也触发焦点校验，可能被误放行。

**如果你不接受这个风险**，把 `DshaIslandModule.recentlyArmed()` 里的全局窗口去掉，
只保留“参数含包名”与线程窗口；代价是部分 HyperOS 版本可能解锁失败，退回胶囊。

---

## 10. 如果 hook 失效

HyperOS 的私有类会随版本变化。按顺序排查：

1. `adb logcat -s DshaIsland` 看哪一条 hook 缺失；
2. 用 jadx 打开 SystemUI / `miui.systemui.plugin`，确认：
   - `com.miui.systemui.notification.MiuiBaseNotifUtil#generateInnerNotifBean(StatusBarNotification)` 是否存在；
   - `miui.systemui.notification.NotificationSettingsManager#canShowFocus/canCustomFocus` 是否还在；
   - `miui.systemui.notification.focus.SignatureChecker#checkSignatures` 是否还在；
3. 如果类被移到插件里，确认 `PluginInstance$PluginFactory#createPluginContext` 的 hook 是否命中；
4. 只改类名/方法名，不要改 payload；payload 是官方模板，和版本无关。

失败时的降级顺序：

```
官方 param 大岛
  ↓ 解锁失败
promoted + shortCriticalText 胶囊
  ↓ 类名全变
普通通知（DSHA 原样）
```

任何一层失败都不会阻断 DSHA 的通知。

---

## 11. 卸载 / 回滚

1. LSPosed 管理器 → 停用 **DSHA Island**；
2. 重启；
3. 需要的话卸载 APK。

DSHA 没有任何文件/签名/数据被修改，回滚后通知恢复原样。

---

## 12. 已知限制

- **未在真机安装验证**：当前环境禁止 `pm install`，只做了编译、签名、APK 结构检查；
- 终端态（任务完成）DSHA 的通知不是 ongoing，岛可能只闪一下；需要 P0 观察后决定是否在模块里补 `FLAG_ONGOING_EVENT` + 8 秒后取消；
- `miui.focus.param` 仍可能被系统按 AppId/签名拒绝；模块的解锁只覆盖已知的校验点，不保证所有 HyperOS 小版本；
- 不支持岛内“批准/拒绝”；
- 只适用于 HyperOS 3/4（Android 16/17），其他系统不启用；
- 这是非官方接口，系统升级可能失效，仅限个人设备使用。

---

## 12.5 免 root 部署（DSHA Island Lite）

> 免 root 到底能做到哪一步、哪些做不到：见 **[免 root 能力边界](docs/免root能力边界.md)**。

### 组成

- `lite/`：伴生 App（`io.github.dsha.island.lite`），前台服务 + 本机 HTTP sink + promoted 通知；
- `plugin/`：DSHA 用户插件（`dsh-island-lite`），监听 `session/event`，推送到 `127.0.0.1:8798`。

### 安装

1. 安装 `out/current/dsha-island-lite-0.4.1.apk`；
2. 打开 App → 允许通知 → 点「启动岛服务」；
3. 在 DSHA 插件页安装 `out/current/dsh-island-lite-0.4.1.tar.gz`，重启一次 DSH/Web；
4. 回桌面跑一个任务，岛上应出现 `DSHA 图标 + 动作 + 内容`。

### root 路线显示路径（0.1.4）

插件启动一个**只读本机接口** `127.0.0.1:8797/state`，root 模块每秒轮询一次，**不需要安装 Lite**：

```json
{"state":"writing","text":"/root/x.java","startedAt":1793035126843}
```

- 大岛 A 区：`写入` + `/root/x.java`
- 大岛 B 区：`12s`（当前动作耗时）
- 展开卡片：`DSHA` + `任务总耗时 3分10秒` + `/root/x.java`
- 停止按钮：固定「停止」

数据来源顺序：**插件 8797 → Lite 桥（可选）→ DSHA 通知阶段/总秒数**。插件没运行或接口不可达时自动降级。

> 8797 只绑定 `127.0.0.1`；同机其他 App 理论上可以读这条状态（含路径/命令），仅供个人设备使用。

### 显示规则（Lite 0.3.1）

| 事件 | 岛上短文本示例 |
|---|---|
| 回合开始 | `思考中` |
| 读文件 | `读取 /root/DSHA/README.md` |
| 写文件 | `写入 Notifier.java` |
| 执行命令 | `执行 ls -la /tmp` |
| 联网 | `搜索 HyperOS 超级岛` |
| 清单 | `清单 1/2 写代码` |
| 等确认 | `等你确认` |
| 完成 | `任务完成` |

活动状态下每秒刷新一次耗时，通知正文会变成 `写入 Notifier.java · 12s`。

### Shizuku（可选）

Lite 本身**不需要 Shizuku**。有 Shizuku 时可以省几步：

```bash
# Shizuku shell 里执行
pm grant io.github.dsha.island.lite android.permission.POST_NOTIFICATIONS
dumpsys deviceidle whitelist +io.github.dsha.island.lite
```

### 边界

- 只有胶囊，没有官方大岛/岛上按钮；
- 停止任务请回 DSHA 或下拉通知栏；
- 端口固定 `8798`，避免和 WorkIsland 的 `8799` 冲突；
- 任何本机 App 都能调用这个 HTTP 端口伪造显示，仅供个人设备使用。

## 13. 更新日志

完整历史、组件版本映射和版本策略见 [CHANGELOG.md](CHANGELOG.md)。

- **0.4.1**：检查更新增加 GitHub Release 302 跳转兜底，API 限流/403 时仍能识别最新版本。
- **0.4.0**：Lite 新增「检查更新」（点按才访问 GitHub API）和「免 root 能力报告」；新增 `docs/免root能力边界.md`，明确免 root 只能到 AOSP 胶囊/卡片，官方大岛与岛上停止仍需 root。
- **0.3.1**：修复卡住/重复：不再因旧 `miui.focus.param` 跳过更新；通知监听器增加兜底 enrich；直读状态缓存 12s；插件状态服务跨 HMR 复用、自动重试，并带每秒心跳，失联后自动降级。
- **0.3.0**：新增平台适配层（Xiaomi/OPPO/vivo/Huawei/Honor）；ColorOS 16 走 AOSP Live Updates；vivo/华为无公开私有接口时降级普通通知。
- **0.2.0**：模块 / Lite / 插件 / Release 统一版本号；root 直读插件 8797；活动摘要不上岛、终态摘要保留完成通知；停止按钮去重；展开卡片显示任务总耗时。
## 14. 设计来源

- 小米超级岛开发指南 / 模板库（`miui.focus.param`、`param_v2`、`imageTextInfoLeft` 等字段）；
- HyperIsland（LSPosed，OS3/4）公开的 hook 点：`MiuiBaseNotifUtil`、`NotificationSettingsManager`、`SignatureChecker`、`PluginFactory`；
- AOSP `Notification`：`EXTRA_REQUEST_PROMOTED_ONGOING`、`EXTRA_COLORIZED`、`EXTRA_SHORT_CRITICAL_TEXT`（`android.shortCriticalText`）。

本项目的代码为独立实现，不包含 WorkIsland 的代码。
