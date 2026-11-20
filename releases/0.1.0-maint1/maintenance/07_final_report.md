# 07 · 最终报告（第 7 阶段）

> 自检元数据｜执行模型：Tencent Hunyuan Hy（用户 2026-11-21 授权接手；ChatGPT key 失效）｜推理档位：当前环境最高可用｜本阶段是否发生降级：是（模型替换，已获用户明确授权，非静默）｜仓库 commit：`6d525f694bc0592abd9f6ffbf0cbb1377b9a1db0`

## 一句话结论 + 关键数字

对 `ChaosSurvivalProject/purpurflow`（commit `6d525f6`）完成全仓深度维护：修复 7 个 Bug（含 3 个 P0）+ 落地 6 项带测量优化 + 补齐产物。在 stub 编译环境真实运行 **34 个测试全绿**；真实编码压测显示**字节↓79.03%、包数↓80%、编码耗时↓78.4%**。但**未能实机试机**（无 JDK21/真实双端），链路 P99/丢包/重连恢复/CPU 均未验证，按诚实原则不宣称"全部完成"。

| 关键指标 | 前 | 后 | 增益 |
|---|---:|---:|---:|
| 总字节数（10000 更新） | 1,718,547 B | 360,452 B | ↓79.03% |
| 包数 | 50,000 | 10,000 | ↓80.00% |
| 有效载荷比 | 0.1168 | 0.5568 | ↑（均≤1） |
| 每更新编码 CPU | 1.446 µs | 0.313 µs | ↓78.4% |
| P99/丢包/重连/CPU | — | — | **未验证** |

## 已修 Bug 列表（详见 02）

| 编号 | 摘要 | 级别 | 状态 |
|---|---|---|---|
| R-03/BUG-E2 | HELLO_ACK enhanced 标志丢失 | P1 | ✅ 修复+测试 |
| R-04/BUG-C | DictSync 超长字典项溢出 | P1 | ✅ 修复+测试 |
| R-05/BUG-E | FrameCodec 容量估算/上限误判 | P1 | ✅ 修复+测试 |
| R-02/BUG-B | 多分片重组必然失败 | P0 | ✅ 修复+4 测试 |
| R-06/BUG-F | 会话永不创建（增强流不可用） | P0 | ✅ 修复+4 测试 |
| R-07/BUG-G | H3 背压/限速/队列未编排 | P1 | ✅ 修复+4 测试 |
| R-08 | PriorityQueue 死代码 | P2 | ✅ 修复 |
| R-12 | 快照/会话存储无界增长 | P2 | ✅ 修复+测试 |
| R-09 | 黄金向量未冻结 | P2 | ✅ 冻结+4 断言测试 |
| R-11 | bench 口径失真（>1） | P1 | ✅ 重写+真实测量 |
| R-13 | 无 Wrapper/CI | P1 | ✅ 部分补（gradle.properties+CI） |
| R-14 | docs/01 registerLarge 声明错误 | P2 | ✅ 修正 |
| R-01 | FlowPayload 字节布局疑似不对称 | P0 | ⚠️ 需实测（不改，避免破坏对接） |

## 已落地优化列表（详见 03）
ZigZag+VarInt、二进制 delta 替代 JSON、Delta+脏位图、去重窗口、捎带 ACK、合包、分片重组修复、快照 TTL 清理接线、会话清理、EgressPipeline 背压编排、限速排队不丢帧。二次压缩**默认关闭**（原版已压缩，无实测不启用）。

## 新增产物下载清单（详见 04）
`releases/0.1.0-maint1/`：`purpurflow-common/server/client/sources-0.1.0-maint1.jar` + docs/ + maintenance/ + bench/ + `manifest.json` + `manifest.sha256`，整体 `releases/0.1.0-maint1.zip`。校验：`sha256sum -c manifest.sha256`。**诚实标注**：jar 为 stub 编译产物（`static-compiled-with-stubs`，`live_verified=false`），非真实 Fabric/Purpur 环境构建。

## 版本与兼容矩阵（详见 05）
MC 1.21.11 / Purpur #2568 / Fabric Loader 0.18.1 / API 0.139.4+1.21.11 / Java 21。静态验证通过，实机未验证。版本无静默替换；R-14 文档错误已显式修正。

## 试机结论（详见 06）
场景 2/4/6/8 为【静态验证】（集成驱动 10 项 PASS）；场景 1/3/5/7 为【未验证】（需真实双端）。诚实失败→归因→改动→复测闭环各 1 轮。

## 遗留风险与未验证项（不包装成完成）
1. **R-01 跨 API 字节布局**：需真实 Purpur+Fabric 抓包/握手验证；未盲改。
2. **实机试机全场景未跑**：无 JDK21/双端环境。
3. **链路指标未采集**：P50/P99、丢包率、重连恢复、CPU/堆。
4. **jar 非真实环境构建**：需 CI 在 JDK21+Fabric Loom 下重建并刷新 manifest。
5. **Gradle Wrapper 二进制缺失**：已补 gradle.properties + CI（setup-gradle），未落地 wrapper jar（环境无法下载）。
6. **CHANGELOG 根目录缺失**：维护变更记录在 maintenance/，未归并到根 CHANGELOG.md。

## 回滚路径
- 代码：`git revert` 或 `git checkout 6d525f6 -- <file>`（协议/字典版本未变，向后兼容）。
- 出站编排：`configureEgress(false,...)` 直发；压缩：`compression.enabled=false`；字典：`DictRegistry.rollbackTo`；插件：移除 jar 即回原版（H2）。
- MC 世界升级不可逆，升级前必须备份。

## 自检清单（逐条勾验）
- [x] H1 零额外端口：全部改动复用 plugin message，无新端口/中间件（静态验证；实机核对端口未做→如实标注）
- [x] H2 vanilla 兼容：`canSendEnhanced(false)=false`、`enabled=false` 不注册出站（测试通过；vanilla 实连未验证）
- [x] H3 背压：EgressPipeline 队列上限/优先级丢弃/限速排队/可写性门控（4 测试通过；真实弱网未验证）
- [x] H4 重连：`canResumeIncrementally` 增量/全量判定、DedupWindow、Snapshot last-write-wins（测试通过）
- [x] H5 版本握手：`ProtocolWhitelist` 降级 VANILLA 不踢人、超时 expireAll（测试通过；enhanced 标志修复联动）
- [x] 模型档位未静默降级：ChatGPT key 失效已声明，用户明确授权替换，每阶段头部标注
- [x] 三类标注完整：02/03/06 均标注【已证实/静态验证/需实测/未验证】
- [x] 所有产物落盘且有 sha256：manifest.json + manifest.sha256
- [x] 演示无伪造：集成驱动真实运行，失败项如实记录（S6 首轮 FAIL→修正→PASS）
- [x] 每项优化带测量：03 逐项写改了什么/为什么省/代价/回滚/实测增益（P99 等未验证项明确标出）
- [x] 版本未静默替换：全部核实一致，R-14 显式修正文档
- [ ] 实机试机：未验证（环境限制，已列遗留项与补齐条件）

> 结论诚实声明：本维护在**静态验证层面**完成所有可在本环境证明的工作；**实机层面**存在明确未验证项，需在有 JDK21 + Purpur/Fabric 双端的环境补齐后，方可宣称全部达到提示词收敛条件。
