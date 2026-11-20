# 05 · 版本核查与深度维护（第 5 阶段）

> 自检元数据｜执行模型：Tencent Hunyuan Hy（用户 2026-11-21 授权接手）｜推理档位：当前环境最高可用｜本阶段是否发生降级：是（模型替换，已获授权）｜仓库 commit：`6d525f694bc0592abd9f6ffbf0cbb1377b9a1db0`

---

## 1. 版本核查（网络核实，2026-11-20）

| 组件 | 仓库声明 | 核实结果 | 状态 |
|---|---|---|---|
| Minecraft Java 1.21.11 | 存在，Java 21，最后混淆版本 | 存在，2025-12-09 发布，协议 774，Java 21，最后一个 Mojang 混淆版本 | ✅ 已证实 |
| Purpur 1.21.11 | `org.purpurmc.purpur:purpur-api:1.21.11-R0.1-SNAPSHOT` | 存在，稳定 build #2568（2025-12 发布，33 个构建），要求 Java 21；MIT 许可 | ✅ 已证实 |
| Fabric Loader | 0.18.1 | 与 1.21.11 匹配 | ✅ 已证实 |
| Fabric API | 0.139.4+1.21.11 | 存在；`registerLarge` 此版本**只有 int 重载** | ✅ 已证实 |
| Fabric Loom | 1.14-SNAPSHOT | 支持 1.21.11 + Mojang mappings | ✅ 已证实 |
| Java | 21 | MC 1.20–1.21.11 均要求 Java 21；**26.1+ 起要求 Java 25** | ✅ 已证实 |
| PacketEvents | 2.11.0 起支持 1.21.11 | 维护活跃 | ✅ 已证实（沿用 docs/01） |

### R-14 修正（本次落地）
- `docs/01-事实核实.md:108` 原声称 `registerLarge` 的 `maxPacketSize` 可传 `IntSupplier` 动态决定——**错误**。
- 锁定的 Fabric API `0.139.4+1.21.11` 中 `PayloadTypeRegistry.playS2C().registerLarge(Type, StreamCodec, int)` **只接受固定 int**；`IntSupplier` 重载是更高版本才引入。
- 代码 `PayloadRegistrations.java` 用的是 `int` 重载，**代码正确**，仅文档错误。已修正文档，明确自适应分片由自研 `Fragmenter` + 握手协商的 `serverMaxChunk` 实现，不依赖 Fabric 动态重载。

### 无静默替换
- 所有版本号均与仓库声明一致；**没有发现不存在或写错的版本号**，故无需替换。唯一差异是上述文档准确性（R-14），已显式修正而非静默改代码。

## 2. 兼容矩阵

| MC | Purpur | Fabric Loader | Fabric API | Java | 插件版本 | 验证状态 |
|---|---|---|---|---|---|---|
| 1.21.11 | #2568 | 0.18.1 | 0.139.4+1.21.11 | 21 | 0.1.0-maint1 | **静态验证**（stub 编译 68 class + 30 测试通过；未实机） |
| 1.21.11 | #2568 | 0.18.1 | 0.139.4+1.21.11 | 21 | 0.1.0-maint1 | **未验证**（真实 Purpur 加载 / Fabric 客户端进服） |

> 诚实标注：本环境无 JDK21/无真实服务端，未做实机加载。静态验证只证明源码类型自洽与逻辑行为，不等同于"实跑通过"。

## 3. 升级执行与破坏性 API 迁移

### 3.1 关键迁移路径（1.20.5+ Fabric networking）
- 1.20.5 起 networking 从 `Identifier + PacketByteBuf` 迁移到 `CustomPacketPayload + StreamCodec + PayloadTypeRegistry`。本工程已采用新 API（`FlowPayload implements CustomPacketPayload`、`StreamCodec.of`、`registerLarge`/`register`），**无遗留旧 API**。
- configuration 与 play 阶段分离：本工程仅在 play 阶段注册 receiver（`PurpurFlowMod.onInitializeClient` 注释 H1），规避登录时序坑。
- `ResourceLocation` → `Identifier`（Mojang mappings）：代码已用 `Identifier.fromNamespaceAndPath`，正确。

### 3.2 未来升级清单（MC 26.x / Java 25）
1. Java 工具链 21 → 25（`build.gradle` 的 `JavaLanguageVersion.of(21)` 与 `gradle.properties`）。
2. Fabric Loader/API/Loom 升到支持目标 MC 的版本；`registerLarge` 的 `IntSupplier` 重载届时可用，可评估替换自研分片。
3. Purpur 26.x build + purpur-api 坐标更新。
4. MC 26.x 若结束混淆命名（当前 1.21.11 是最后混淆版本），需重新核对 mappings 选择。
5. **每次升级必跑回归**：30 个测试 + 黄金向量 + 双端试机（第 6 阶段清单）。

## 4. 发布管理

- **版本号**：semver。基线 `0.1.0`，本次维护产物 `0.1.0-maint1`（维护增量，不破坏 API）。
- **tag / release**：建议 `v0.1.0-maint1`，release notes 覆盖"新增/修复/破坏性/升级/回滚"五段（见 `07_final_report.md`）。
- **CHANGELOG**：本次维护变更集中在 `maintenance/02_bugfix.md`、`03_optimization.md`；正式发布应归并到根 `CHANGELOG.md`（当前缺失，已列入遗留项）。
- **产物归档**：`releases/0.1.0-maint1/` + 整体 zip + manifest.json（sha256）。

## 5. 长期维护清单

| 维度 | 措施 |
|---|---|
| 依赖过期监控 | 依赖版本集中在 `gradle.properties`；定期核对 MC/Purpur/Fabric/Java 官方发布页 |
| 上游版本跟进 | 关注 Purpur build 号、Fabric API 版本、PacketEvents 1.21.11+ 支持 |
| 协议版本白名单 | `ProtocolWhitelist` + `ProtocolVersion`；字典版本回退走 `DictRegistry.rollbackTo` |
| 分层开关（一键关闭） | 总开关 `enabled=false`；可选包拦截层 `optional.packetevents.enabled=false`；压缩层 `compression.enabled=false`；出站编排 `configureEgress(false,...)` 直发；H1 始终无额外端口 |
| 升级回归清单 | 30 测试 + GoldenVectorTest + 双端试机 8 场景（第 6 阶段） |

## 6. 回滚路径（每步可回到上一个可用版本）

| 变更 | 回滚命令/操作 | 数据兼容 |
|---|---|---|
| 本次代码修改 | `git revert <commit>` 或 `git checkout 6d525f6 -- <file>`（保留 maintenance 产物） | 协议版本未变，字典版本未变，向后兼容 |
| 出站编排 | `dispatcher.configureEgress(false,...)` | 无状态迁移 |
| 压缩 | `compression.enabled=false` | 无 |
| 字典版本 | `DictRegistry.rollbackTo(oldVersion)` | 需客户端字典同步（DICT_SYNC） |
| 依赖升级失败 | 恢复 `gradle.properties` 版本 + `git checkout` build.gradle | 世界数据不降级（MC 世界升级不可逆，需备份） |
| 插件不可用 | 从 `plugins/` 移除 jar，服务端行为回到原版（H2） | 无 |

> 世界升级不可逆：MC 世界升级后不能降级，任何 MC 版本升级前必须备份世界与配置（网络核实结论）。
