> ## 星穹旅驿出品 · Chaos Survival Project
> 
> 本插件/模组由 **星穹旅驿（Chaos Survival Project）** 团队开发维护。
> 遇到问题或有功能建议？请到 **GitHub** 提交 Issue 反馈，我们会尽快处理。
> This plugin/mod is developed by **星穹旅驿 (Chaos Survival Project)**. Found a bug or have a suggestion? Please report it on [GitHub Issues](https://github.com/ChaosSurvivalProject/purpurflow/issues).
> 
> 🔗 GitHub 仓库 / Repo: [https://github.com/ChaosSurvivalProject/purpurflow](https://github.com/ChaosSurvivalProject/purpurflow) ｜ 反馈 / Issues: [https://github.com/ChaosSurvivalProject/purpurflow/issues](https://github.com/ChaosSurvivalProject/purpurflow/issues)

---

# PurpurFlow — 双端信息流带宽与稳定性工程

<p align="center">
  <img src="assets/images/banner.png" alt="purpurflow 宣传图" width="820">
</p>


> Purpur 1.21.11 + Fabric 1.21.11 深度协同，用**更少的带宽**传输**更大的信息量**，且更稳定。
> 命名空间 `purpurflow`，通道 `purpurflow:main`（不使用 `minecraft:` 保留命名空间）。

本仓库是一套**已设计好、可直接落地**的工程骨架与配套设计文档，严格按照提示词的六个阶段交付：

```
事实核实 → 信息流盘点与选型 → 架构与帧规范 → 稳定性四约束落地
        → 工程结构与代码骨架 → 测试/压测/验收 → 风险与回滚 → 自检清单
```

## 效果预览

<p align="center">
  <img src="assets/images/demo.png" alt="purpurflow 演示图" width="720">
</p>


## 硬性约束（不可协商）

| 编号 | 约束 | 一句话 |
|---|---|---|
| **H1** | 零额外端口 / 单连接复用 | 100% 复用玩家已建立的 MC TCP 连接的 custom payload，禁止任何新端口/新连接/中间件 |
| **H2** | 原版客户端兼容 | 未装 Mod 的 vanilla 客户端正常进服、正常游玩、不被踢、不卡顿 |
| **H3** | 背压与拥塞控制 | 弱网/卡顿不雪崩：独立队列 + 可写性门控 + 优先级丢弃 + 限速 + 熔断 |
| **H4** | 重连状态补偿 | 断线重连/切服不丢不重：增量补发优先，全量快照兜底，状态幂等 |
| **H5** | 版本握手校验 | 版本/字典/能力位图不匹配时安全降级，绝不崩服、踢人、写脏 vanilla 连接 |

**目标的可验收翻译**：用更少带宽传更多信息 = ①单条逻辑信息编码字节数下降；②每条信息摊到的帧/包开销下降；③无效与重传字节下降。以**有效载荷比**与**每逻辑更新平均字节**衡量，且 P99 延迟、重连恢复时间、CPU 增量必须同时达标。

## 事实核实结论（摘要，详见 [docs/01](docs/01-事实核实.md)）

| 项 | 结论 | 标注 |
|---|---|---|
| MC Java 1.21.11 | 2025-12-09 发布，协议版本 774，最低 Java SE 21 | 已证实 |
| Purpur 1.21.11 | 存在（稳定 build #2568）；`org.purpurmc.purpur:purpur-api:1.21.11-R0.1-SNAPSHOT` | 已证实 |
| Fabric 1.21.11 | API `0.139.4+1.21.11`，Loader `0.18.1`，Loom `1.14`；1.21.11 是最后一个混淆版本 | 已证实 |
| `PayloadTypeRegistry.registerLarge(...)` | Fabric 官方分包 API，可发送超过 vanilla 限制的 payload | 已证实 |
| custom payload 上限 | **下行 clientbound = 1 MiB(1,048,576 B)，上行 serverbound = 32 KiB** — 上下行不对称 | 已证实 |
| `network-compression-threshold` | `server.properties` 项，默认 256，`-1` 禁用 | 已证实 |
| PacketEvents | 2.11.0（2025-12-09）起支持 1.21.11，维护活跃 | 已证实 |

> ⚠️ 上行仅 32 KiB 是硬约束：客户端→服务端的 `HELLO`、能力位图、ack 必须极简；大消息只走下行，且下行也要按 1 MiB 分片。

## 工程结构

```
purpurflow/
├─ common/     纯 Java 21、零 MC 依赖，双端共享
│   └─ src/main/java/com/purpurflow/common/
│       ├─ protocol/  Frame, FrameType, Flags, VarInts, BitPacker, FrameCodec, FrameException
│       ├─ dict/      DictRegistry, DictSnapshot, DictSync
│       ├─ state/     Snapshot, Delta, DirtyBitmap, SeqTracker, DedupWindow
│       └─ control/   Capability, Hello, HelloAck, Resume, Heartbeat, ControlFrame
├─ server/     Purpur 插件（Bukkit/Paper plugin messaging）
│   └─ src/main/java/com/purpurflow/server/
│       ├─ PurpurFlowPlugin.java
│       ├─ channel/  ChannelRegistrar, InboundDispatcher
│       ├─ session/  PerPlayerSession, Handshake, CapabilityNegotiator, ResumeService, SnapshotStore
│       ├─ egress/   EgressGate, PriorityQueue, BackpressureGate, RateLimiter, Batcher, Fragmenter
│       └─ config/   Config, ProtocolWhitelist
├─ client/     Fabric Mod（fabric.mod.json + entrypoint）
│   └─ src/main/java/com/purpurflow/client/
│       ├─ PurpurFlowMod.java
│       ├─ net/     FlowPayload, PayloadRegistrations, Outbound, Inbound, Reassembler
│       ├─ session/LocalMirror, AckReporter, ResumeHandler
│       └─ compat/  CapabilityProbe, VanillaFallback
├─ test/       JUnit 单测 + 集成测试 + 黄金向量 + 压测脚本 + 验收清单
└─ docs/       设计文档 01–08
```

## 关键接口契约（第四阶段强制）

```java
// 帧编解码
byte[]                  FrameCodec.encode(List<Message> msgs) throws FrameException;
List<Message>           FrameCodec.decode(byte[] frame)       throws FrameException;

// 出站背压闸门：false = 背压丢弃，调用方必须处理
boolean                 EgressGate.offer(Player player, Frame frame, Priority p);

// 重连补偿
ResumePlan              ResumeService.resume(UUID sessionId, long epoch, long ackSeq);
// ResumePlan = FULL_SNAPSHOT | DELTA(fromSeq)

// 能力协商
ProtocolVersion         CapabilityNegotiator.negotiate(Capability client, Capability server);
// 不匹配返回 DISABLED
```

## 分层（L0–L6）

| 层 | 职责 |
|---|---|
| L0 承载 | play 阶段 custom payload，通道 `purpurflow:main` |
| L1 帧层 | 一帧多消息合包 + 大消息分片重组（下行用官方 `registerLarge`，上行自研分片） |
| L2 编码 | VarInt/ZigZag、bitfield、字典 ID、BlockPos 打包、定点化 |
| L3 语义 | 状态快照 + delta + 脏位图 + 幂等序号 + 可靠性分级 |
| L4 压缩 | 帧级自适应、可插拔、**默认关闭** |
| L5 控制 | 握手、字典同步、心跳/RTT、重连补偿、优先级与速率 |
| L6 兼容 | 能力探测与 vanilla 降级 |

## 文档导航

1. [事实核实](docs/01-事实核实.md) — 版本/API/限值，三类标注与来源
2. [信息流盘点与选型](docs/02-信息流盘点与选型.md) — 四类信息流统一维度表
3. [架构与帧规范](docs/03-架构与帧规范.md) — 分层、帧格式、字典表、握手时序图、字节对照表
4. [稳定性四约束落地](docs/04-稳定性四约束.md) — H1–H5 逐条实现
5. [工程结构与代码骨架](docs/05-工程结构与代码骨架.md) — 构建坐标、接口、核心路径伪代码
6. [测试/压测/验收](docs/06-测试压测验收.md) — 单测/集成/压测口径/验收门槛
7. [风险与回滚](docs/07-风险与回滚.md) — 逐层风险、回滚路径
8. [自检清单](docs/08-自检清单.md) — 逐条勾验

## 构建

```bash
# common + server（标准 Gradle）
./gradlew :common:build :server:build

# client（Fabric Loom）
./gradlew :client:build

# 全部测试
./gradlew test
```

> 本工程为协议与骨架实现，不含游戏玩法逻辑，不修改原版协议，不引入任何需要额外部署的服务。

---

## 🌌 星穹旅驿全家桶 / Chaos Survival Project

> 本插件/模组由 **星穹旅驿（Chaos Survival Project）** 出品。探索我们的全部开源项目：

- 🌐 **官方网站**：https://chaossurvivalproject.github.io/
- ⭐ **GitHub 组织 / 源码与反馈**：https://github.com/ChaosSurvivalProject
- 📦 **Modrinth 下载（24 项目）**：https://modrinth.com/organization/chaossurvivalproject
- 📖 **MC百科词条**：https://www.mcmod.cn/

遇到问题或有功能建议？请到本仓库的 [Issues](https://github.com/ChaosSurvivalProject/purpurflow/issues) 反馈 —— 星穹旅驿团队倾力维护。

*星穹旅驿出品 · 开源免费 · 欢迎 Star ⭐*
