# Changelog

本项目从 **0.2.0** 起，LSPosed 模块、Lite 伴生 App、DSHA 插件和 GitHub Release
共用同一个版本号；修复递增 patch，功能递增 minor。历史 0.1.x 是组件各自版本，
映射见文末。

## [Unreleased]

### Changed

- 许可证保持不变，仍为 **MIT**，全文见 `LICENSE`。
- 撤回 2026-10-27 工作树中改为 GPL-3.0-only 的未发布改动；该改动从未随任何
  Release 发布，因此全部版本（含 v0.4.1 及更早）均按 MIT 分发。

## [0.4.1] - 2026-10-27

### Fixed

- Lite 和 root 模块设置页都支持「检查更新」；API 匿名限流/403 时会失败，增加
  `github.com/.../releases/latest` 302 跳转兜底，仍能识别最新 tag。
- 兜底路径不返回 Release 正文，但版本号和跳转链接正常。

## [0.4.0] - 2026-10-27

### Added

- Lite 新增「检查更新」：点击时请求 GitHub `releases/latest`，显示当前/最新版本、
  Release 说明和跳转按钮；不后台轮询、不自动下载。
- Lite 新增「免 root 能力报告」：按 OEM、Android 版本、`canPostPromotedNotifications()`
  显示系统能不能上 Live Updates。
- 新增 `docs/免root能力边界.md`：记录 HyperOS 实测结论，以及 ColorOS/vivo/Huawei 的公开边界。

### Changed

- 三端统一版本号 `0.4.0`。

### Notes

- 免 root 在 HyperOS 的上限仍是 AOSP 胶囊：没有官方大岛、没有岛上停止。
- ColorOS 16 走 AOSP Live Updates 可显示流体云卡片；OriginOS/HarmonyOS 取决于系统是否开放。
- 官方大岛/私有原子通知/实况窗仍需 root 系统级 hook 或厂商 SDK，不在免 root 范围。

## [0.3.1] - 2026-10-27

### Fixed

- 岛偶尔卡住不更新：不再因为通知 extras 里已有旧的 `miui.focus.param`
  就直接 return；每次富化前清理旧 param/pics/actions。
- `MiuiNotificationListener` 增加 DSHA 通知的兜底 enrich，避免只依赖
  `MiuiBaseNotifUtil` 的类加载路径而漏更新。
- 路径/详情时有时无：插件直读状态缓存从 3s 延长到 12s；Lite 桥接缓存 10s。
- 插件 8797 状态服务改为 `globalThis` 共享，HMR 后复用旧 server；
  `listen` 失败自动 2s 重试，避免端口占用导致详情永久丢失。
- 插件每秒写 `aliveAt` 心跳；模块 12 秒无心跳即退回 DSHA 通知，
  避免插件被停用后一直显示旧路径。
- 日志增加 `src=direct|bridge|notif`，可直接看出详情来源。

## [0.3.0] - 2026-10-27

### Added

- 平台适配层：自动识别 Xiaomi / OPPO(一加/realme) / vivo(iQOO) / Huawei / Honor / 其他。
- ColorOS 16 走 AOSP Live Updates（promoted ongoing + shortCriticalText），对应「流体云」。
- vivo / Huawei / Honor 在系统实现 Android 16 Live Updates 时尽力上岛；否则普通通知降级。
- Lite 通知频道按平台命名（超级岛 / 流体云 / 原子通知 / 实况窗）。
- root 模块增加 Xiaomi 设备 guard，其他品牌自动 inactive。

### Changed

- 三端统一版本号 `0.3.0`。
- Lite 增加 `setColorized(true)`，满足 AOSP Live Updates 的特征要求。

### Notes

- 华为 HarmonyOS 4 的 Android APK 无法使用实况窗；HarmonyOS NEXT 不能安装 APK。
- vivo 原子通知、OPPO 流体云 UPK、华为实况窗的私有 SDK 需要厂商资质/白名单，本版本未接入。

## [0.2.0] - 2026-10-27

### Changed

- 统一版本号：模块 / Lite / 插件全部为 `0.2.0`，Release 为 `v0.2.0`。
- root 模块直接轮询插件 `127.0.0.1:8797/state`，不再依赖 Lite 状态桥。
- 展开卡片固定显示「任务总耗时 X」；岛上 B 区显示当前动作耗时。

### Fixed

- 活动态通知组摘要不再上岛，避免两个岛。
- 终态组摘要放行，任务完成后恢复「任务完成」通知（8 秒收起）。
- 停止按钮去重：`hintInfo.title` 改为「任务控制」，只保留按钮「停止」。
- 岛上/卡片/按钮不再重复「思考中」和秒数。

## [0.1.7] - 2026-10-27

### Fixed

- 完成通知丢失：DSHA 终态子通知约 1 秒后被 cancel，完成态实际挂在
  `id=0` 的组摘要上；改为放行终态摘要，活动摘要仍跳过。

## [0.1.6] - 2026-10-27

### Fixed

- 两个岛：跳过 `id=0` / `FLAG_GROUP_SUMMARY` 的通知组摘要。

## [0.1.5] - 2026-10-27

### Fixed

- 停止按钮重复：`hintInfo.title` 从「停止」改为「任务控制」，
  `actionInfo.actionTitle` 保留「停止」。

## [0.1.4] - 2026-10-27

### Added

- root 模块直读插件 `8797/state`，显示动作 + 路径/命令 + 当前动作耗时。
- 展开卡片显示任务总耗时。

### Fixed

- 去掉 `ticker`/AOD/`baseInfo.subTitle`/原通知正文里的重复阶段和秒数。
- 重写原通知标题/正文为 `DSHA` / `任务进行中`，避免叠字。

## [0.1.3] - 2026-10-27

### Fixed

- 跳过 `FLAG_FOREGROUND_SERVICE` 和 `id=1001`，前台服务通知不再上岛。
- 只处理 `dsh_task_progress` 任务频道。

## [0.1.2] - 2026-10-27

### Added

- 停止按钮改用 `hintInfo.actionInfo` 的带文字按钮。

### Fixed

- 过滤 `dsh_harness_channel` 前台服务频道。

## [0.1.1] - 2026-10-27

### Note

- 未发布。内部构建序号，没有公开 APK；功能并入 0.1.2。

## [0.1.0] - 2026-10-27

### Added

- 首个可编译的 SystemUI LSPosed 模块：识别 DSHA 通知、注入
  `miui.focus.param`、promoted + `shortCriticalText`。
- 免 root Lite 伴生 App + `dsh-island-lite` 插件（胶囊）。
- 构建脚本、安装文档、基础校验。

---

## 历史版本映射

| Release | 模块 | Lite | 插件 |
|---|---|---|---|
| `v0.1.3` | 0.1.3 | 0.1.1 | 0.1.1 |
| `v0.1.7` | 0.1.7 | 0.1.2 | 0.1.2 |
| `v0.2.0` | 0.2.0 | 0.2.0 | 0.2.0 |
| `v0.3.0` | 0.3.0 | 0.3.0 | 0.3.0 |
| `v0.3.1` | 0.3.1 | 0.3.1 | 0.3.1 |
| `v0.4.0` | 0.4.0 | 0.4.0 | 0.4.0 |
| `v0.4.1` | 0.4.1 | 0.4.1 | 0.4.1 |

> `0.1.1` 模块、`0.1.4` 之前的 Release 未逐版发布；从 `0.2.0` 起不再跳号。
