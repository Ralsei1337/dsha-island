# Changelog

本项目从 **0.2.0** 起，LSPosed 模块、Lite 伴生 App、DSHA 插件和 GitHub Release
共用同一个版本号；修复递增 patch，功能递增 minor。历史 0.1.x 是组件各自版本，
映射见文末。

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

> `0.1.1` 模块、`0.1.4` 之前的 Release 未逐版发布；从 `0.2.0` 起不再跳号。
