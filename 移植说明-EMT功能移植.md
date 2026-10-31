# EMT 功能移植说明

把 `litematica-printer-EMT`（反编译源码）的功能移植进本仓库（K1nse_ 分支），包名统一改为 `me.K1nse_.litematica.printer`。
源码规模：213 → **247** 个 `.java`（新增 34 个文件），22,761 → **31,087** 行。

## 已移植功能清单

| 功能 | 主要文件（新增标 N） | 配置键（前缀 `config.litematica-printer.`） |
|---|---|---|
| 挖掘增强 | `enums/FluidAvoidStrategyType`(N)、`utils/PacketSoundConfirmationTracker`(N)、`utils/InteractionUtils`(改)、`handler/handlers/MineHandler`(改)、`mixin/printer/mc/MixinMultiPlayerGameMode`(改) | `breakUsePacket`、`breakSound`、`breakVeilToggle`、`breakVeilList`、`breakParallel`、`breakAvoidFluid`、`breakFluidList`、`breakFluidStrategy`、`breakAvoidSupport`、`breakNonBlocking`、`printSound` |
| NBT 规则 | `utils/BlockNbtRule`(N)、`utils/FilterUtils`(改) | 名单条目支持 `@nbt:<base64url(JSON)>` |
| 声音包确认 | `utils/PacketSoundConfirmationTracker`(N)、`handler/handlers/print/PrintPlacementExecutor`(改)、`mixin/printer/mc/MixinClientPacketListener`(改) | 与 `printSound`/`breakSound`/`printUsePacket`/`breakUsePacket` 联动 |
| 材料替代放置 | `printer/SubstitutePlacementCache`(N)、`guide/guides/SubstituteGuide`(N)、`guide/Guides`(改) | `printSubstitutePlacement`、`printSubstituteList`（格式 `替代1,替代2:目标`） |
| 迭代调度 | `enums/IterationOrderType`(N)、`printer/PrinterBox`(改)、`handler/InteractionBoxTracker`(改) | `printerIteratorMode`、`printerXAxisReverse`、`printerYAxisReverse`、`printerZAxisReverse`、`moveAdaptiveIteration`、`motionAheadBlocks` |
| 背包同步 | `printer/zxy/utils/ZxyUtils`(改)、`config/HotkeysCallback`(改)、`handler/ClientPlayerTickManager`(改) | `syncInventory`(热键)、`syncInventoryCheck`、`syncInventoryCrafter`、`syncPacketLimit`、`syncInventoryColor`、`syncHighlightRenderDistance` |
| 校验器优化 | `printer/verifier/`(N×5)、`mixin/printer/litematica/SchematicPlacementAccessor`(N)、`MixinSchematicPlacement`(N)、`InitHandler`(改) | `verifierOptimized` |
| 自动寻路 | `go/`(N×11)、`printer/SchematicStateCache`(N)、`printer/BlockPosCooldownManager`(N)、`mixin/printer/mc/MixinLocalPlayerGo`(N)、`MixinKeyboardInput`(N)、`MixinDebugRenderer`(N)、`config/builder/DoubleConfigBuilder`(N) | `go*` 全组 + `scanAutoWalkHotkey`(热键)、`/go` 客户端命令 |
| 破基岩 | `utils/bedrock/`(N×4)、`handler/handlers/BedrockHandler`(改)、`utils/mods/ModLoadUtils`(改) | `bedrockExternalMiner` |

## 版本守卫（preprocessor）

- 自动寻路整套（`go/*`、`SchematicStateCache`、`BlockPosCooldownManager`、3 个 go mixin 及其调用点）：`//#if MC >= 126010`（26.1+，恶魂飞行依赖本时间线的 HappyGhast）。
- 背包同步的点击 API（`ContainerInput` vs `ClickType`）：`//#if MC >= 12102`；合成器槽位同步 `//#if MC >= 12004`。
- 其余功能全版本可用（沿用仓库已有的 MC 版本宏风格）。

## 与 EMT 的行为差异（有意为之）

1. `breakParallel` 默认 **true**：本仓库挖掘本来就是「收集候选 + 按工具批量连挖」，与 EMT 并行模式一致；关闭后才是 EMT 的串行逐块挖掘。
2. 背包同步的高亮距离过滤放在 `ZxyUtils.filterBlocksByName`（收集时按 `syncHighlightRenderDistance` 裁剪），EMT 是渲染时裁剪。
3. 未移植 EMT 的 `EatUtils`（自动进食）、`CloudStoreUtils`（云仓库）、`HandRestockShulkerCompat`（手部潜影盒补货）、`MixinMouseHandler`/`MixinLivingEntity`（前者只服务云仓滚轮，后者只服务手部补货）——不在移植清单内。
4. `VerifierActiveUpdate.onScannerVerdict` 的调用点（EMT 的 `SchematicStateCache` 内部）随 `SchematicStateCache` 一并移植，未外接到扫描器主循环；`verifierOptimized` 的核心路径（`MixinSchematicPlacement` 替换校验器实例）已生效。

## 文案

新增配置名/描述/提示同步写入 4 个语言文件：`zh_cn.json`、`zh_tw.json`、`en_us.json`、`lzh.json`，全部 JSON 校验通过。

## 编译验证状态

- 工具链已就绪：`_rev\buildenv\jdk-25.0.5+8`（Temurin JDK 25）、`_rev\buildenv\gradle-9.5.1`（与仓库 `gradle-wrapper.properties` 一致）。
- 本机跑 `:26.1:compileJava` 的已知障碍（与移植代码无关）：loom 在 `LayeredMappingsFactory.writeMapping` 阶段对映射 jar 做 zipfs 写入时抛 `ReadOnlyFileSystemException`（`jdk.zipfs` 在 DSH 子进程文件视图下打开为只读），JDK 21/25、换 GRADLE_USER_HOME、清缓存均复现；子进程对工作区外的写入也被环境拦截（`AccessDeniedException`）。
- 静态检查：无 `me.aleksilassila` 残留、无缺失的 `ModUtils` 引用、全部新增/修改文件括号平衡、4 份语言 JSON 可解析。
- 在普通环境验证的命令（仓库根目录）：

```powershell
$env:JAVA_HOME = "<JDK 25 路径>"
gradlew :26.1:compileJava          # 单版本（26.1 主项目）
gradlew :fabricWrapper:build       # 完整多版本打包
```

如编译报错，优先检查：`fi.dy.masa.litematica` 内部 API 名称随 Litematica 0.26/0.27/0.28 的差异（`OptimizedSchematicVerifier`、`SchematicPlacementAccessor`、`PendingChunkRenderer` 三个文件与 Litematica 版本强耦合），以及 `go` 包对 MC 26.1+ 渲染/实体 API 的引用。
