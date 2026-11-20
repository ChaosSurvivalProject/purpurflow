# 01 · 全仓库深度代码研究（第 1 阶段）

> 自检元数据｜执行模型：Tencent Hunyuan Hy｜推理档位：当前环境最高可用｜本阶段是否发生降级：否｜仓库 commit：`6d525f694bc0592abd9f6ffbf0cbb1377b9a1db0`

> 结论标注三类：**【已证实：文件:行】**（读全代码直接可见）、**【工程推断】**（基于代码结构的合理推断，未实跑）、**【需实测验证】**（需构建/运行才能确认）。本阶段为静态全量阅读，不编造 API/类名/配置项。

---

## 1. 逐文件模块职责表

### common 模块（21 个文件，纯 Java，零 MC 依赖）

| 文件 | 职责 | 关键类/方法签名 |
|---|---|---|
| `protocol/Frame.java` | 帧数据模型（头部+消息列表） | `Frame.data(session,seq,ack,dictVer,flags,msgs)`、`isFragment()` |
| `protocol/FrameType.java` | 消息类型枚举（控制面 0x01–0x09，业务 RAW 0x20） | `fromId(int)`（未知 0x20–0x7F→RAW） |
| `protocol/Flags.java` | flags 位定义与读写 | `has/with/without/reliability/priority` |
| `protocol/VarInts.java` | VarInt/VarLong/ZigZag 编解码 | `writeVarInt/readVarInt/writeVarLong/readVarLong/zigZagEncode/Decode/sizeOfVarInt` |
| `protocol/BitPacker.java` | 64 位布尔打包 | `pack(boolean...)/get/set/count` |
| `protocol/Message.java` | 帧内逻辑消息（type+payload） | `equals/hashCode`（按内容） |
| `protocol/FrameException.java` | 解析异常（RuntimeException） | 两个构造器 |
| `protocol/FrameCodec.java` | 帧编解码核心 | `encode(Frame)`、`encode(List<Message>)`、`decodeFrame(byte[])`、`decode(byte[])`、`setLimits`、`setVerifyCrc` |
| `dict/DictSnapshot.java` | 不可变字典快照 | `idOf/stringOf/diffFrom/applyDelta` |
| `dict/DictRegistry.java` | 多版本字典注册（AtomicReference+历史） | `install/rollbackTo/syncFrom/currentVersion` |
| `dict/DictSync.java` | DICT_SYNC 载荷编解码 | `encode(Payload)/decode(byte[])`、`record Payload` |
| `state/DedupWindow.java` | 滑动去重窗口 | `isNew(seq)/clear/size` |
| `state/DirtyBitmap.java` | 脏位图（long） | `mark/isDirty/merge/clear/count` |
| `state/Delta.java` | 状态增量（baseSeq+entries+脏位图） | `put/merge/isEmpty`、`record Entry(value,version)` |
| `state/SeqTracker.java` | 单调序号追踪 | `next/current/reset` |
| `state/Snapshot.java` | 状态快照（key→value,version） | `put/apply/fullDelta/value/version` |
| `control/Capability.java` | 能力位图 | `supports/intersectFeatures`、5 个 FEATURE 常量 |
| `control/ProtocolVersion.java` | 协议版本枚举 | `fromId`（未知→DISABLED） |
| `control/Hello/HelloAck/Resume/Heartbeat.java` | 控制面消息数据模型 | 各 getter |
| `control/ControlFrame.java` | 控制面载荷编解码 | `encodeHello/decodeHello/encodeHelloAck/decodeHelloAck/encodeResume/decodeResume` |

### server 模块（17 个文件，Purpur 插件）

| 文件 | 职责 | 关键方法 |
|---|---|---|
| `PurpurFlowPlugin.java` | 插件入口 | `onEnable/onDisable` |
| `channel/ChannelRegistrar.java` | 通道注册（H1） | `register/unregister`、`CHANNEL="purpurflow:main"` |
| `channel/InboundDispatcher.java` | 入站分发（PluginMessageListener） | `onPluginMessageReceived/dispatch/sendControl/setSender` |
| `session/Handshake.java` | 握手状态机（H5） | `open/onHello/checkTimeout/close` |
| `session/PerPlayerSession.java` | 每玩家会话状态 | `canSendEnhanced/noteAck` 及各 getter/setter |
| `session/CapabilityNegotiator.java` | 能力协商（H5） | `negotiate/intersectFeatures` |
| `session/SnapshotStore.java` | 快照+有限 seq 日志（H4） | `append/snapshot/canResumeIncrementally/deltasAfter/evictExpired` |
| `session/ResumeService.java` | 重连补偿接口（H4） | `resume` + `record ResumePlan` |
| `session/DefaultResumeService.java` | 补偿默认实现 | `resume` |
| `session/SessionMode.java` | 会话模式枚举 | HANDSHAKING/ENHANCED/VANILLA |
| `egress/PriorityQueue.java` | 每玩家优先级队列（H3） | `offer/poll/size` |
| `egress/Priority.java` | 优先级枚举 | CONTROL(0)/STATE(1)/TELEMETRY(2) |
| `egress/EgressGate.java` | 出站闸门接口（H3） | `offer(player,frame,priority)->boolean` |
| `egress/BackpressureGate.java` | 可写性门控（H3） | `canEmit` + `WritabilityProbe` |
| `egress/RateLimiter.java` | 速率限制（H3） | `tryAcquire(bytes)` |
| `egress/Batcher.java` | 合批（L1） | `batch(messages)` |
| `egress/Fragmenter.java` | 分片（L1） | `split(data,chunkBytes)`、`record Fragment` |
| `config/Config.java` | 强类型配置 | `from(FileConfiguration)` |
| `config/ProtocolWhitelist.java` | 协议白名单（H5） | `accepts/select` |

### client 模块（13 个文件，Fabric Mod）

| 文件 | 职责 | 关键方法 |
|---|---|---|
| `PurpurFlowMod.java` | Mod 入口 | `onInitializeClient/onJoin/onDisconnect` |
| `net/FlowPayload.java` | Fabric 自定义 payload | `record FlowPayload(byte[])`、`CODEC`、`ID`、`TYPE` |
| `net/PayloadRegistrations.java` | payload 注册 | `register()`（playS2C registerLarge + playC2S register） |
| `net/Outbound.java` | C→S 发送 | `hello(dictVersion,localMaxChunk)` |
| `net/Reassembler.java` | 分片重组（L1） | `accept(frame,rawFrameBytes)` |
| `session/Inbound.java` | S→C 处理 | `handle(FlowPayload)` |
| `session/LocalMirror.java` | 本地状态镜像（H4） | `apply/applyFull` |
| `session/AckReporter.java` | ack 上报（H3/H4） | `note/current/reset` |
| `session/ResumeHandler.java` | 重连请求（H4） | `build(epoch)` |
| `compat/CapabilityProbe.java` | 能力探测（L6） | `serverSupports/localCapability` |
| `compat/VanillaFallback.java` | vanilla 降级（H2/H5） | `toVanilla/isVanilla/canSendEnhanced` |

### test 模块

- `ProtocolTest.java`：9 个 JUnit 用例（VarInt/ZigZag 往返、帧往返、CRC、magic、分片、delta 幂等、去重、字典回滚）。注意 `FragmenterProbe` 是**测试内复制的 split 逻辑**，而非直接调用 server 的 `Fragmenter`——两者可能漂移。
- `golden/frame_vectors.md`：5 条黄金向量，但向量 1 的字节串标注"示意"（未冻结真实输出）。
- `bench/bench.py`：口径估算脚本（非真实编码）。

## 2. 跨模块调用边与网络数据流

**入站（C→S）完整链路**：
`client/Outbound.hello()` → `ControlFrame.encodeHello` → `Frame.data` → `FrameCodec.encode` → `FlowPayload(data)` → `ClientPlayNetworking.send` →（play 连接 custom payload）→ `server/InboundDispatcher.onPluginMessageReceived` → `FrameCodec.decodeFrame` → `dispatch`（HELLO→`Handshake.onHello`→HELLO_ACK；RESUME→noteAck；其余占位）。

**出站（S→C）完整链路**：
`InboundDispatcher.sendControl()` → `FrameCodec.encode(Frame.data(...))` → `Player.sendPluginMessage(CHANNEL, bytes)` → `client/PurpurFlowMod` receiver → `new Inbound(...).handle(payload)` → `decodeFrame` → switch（DISABLED/HELLO_ACK/FULL_SNAPSHOT/DELTA）→ `LocalMirror.apply` / `VanillaFallback.toVanilla`。

**状态流转**：`Handshake.open` 创建 `PerPlayerSession`（HANDSHAKING）→ `onHello` 协商成功→ENHANCED / 失败→VANILLA；`SnapshotStore` 按 session 累积 delta 与 seq 日志；重连 `ResumeService.resume` 判定 DELTA/FULL_SNAPSHOT/NONE。

**【工程推断】背压/限速/合批/分片这 6 个 egress 组件虽已实现，但 `InboundDispatcher.sendControl` 未接入 `EgressGate`/`BackpressureGate`/`RateLimiter`/`Batcher`/`Fragmenter`——即 H3 的组件存在但主链路未编排，属于"零件齐全未装车"。**

## 3. 线程模型

- 【已证实】client `registerGlobalReceiver` 的 handler 在主线程执行（`PurpurFlowMod.java:21-26` 注释 + docs/01 引用 Fabric 文档）。
- 【已证实】`InboundDispatcher` 实现 Bukkit `PluginMessageListener`，由服务端主线程回调。
- 【已证实】共享可变状态用 `synchronized`（`DedupWindow`、`SeqTracker`、`SnapshotStore`、`RateLimiter`）。
- 【工程推断】`FrameCodec` 无状态字段（limits 用 volatile），可跨线程共享，但 `setLimits` 运行时改值存在竞态窗口。

## 4. 风险登记表

| 编号 | 位置 | 类型 | 严重度 | 证据（文件:行） | 影响面 |
|---|---|---|---|---|---|
| R-01 | FlowPayload.CODEC | 正确性 | **P0** | `client/net/FlowPayload.java:22-23` | C2S 帧收发字节布局不对称，握手可能失败 |
| R-02 | Reassembler.accept | 正确性 | **P0** | `client/net/Reassembler.java:39-45,52-60` | 多分片帧无法完成重组，且拼接语义错误（含重复头） |
| R-03 | ControlFrame.decodeHelloAck | 正确性/兼容 | **P1** | `common/control/ControlFrame.java:46` | `enhanced` 恒 false，客户端误降级 vanilla |
| R-04 | DictSync.encode | 正确性/安全 | **P1** | `common/dict/DictSync.java:14` | 超长字典项触发 BufferOverflowException |
| R-05 | FrameCodec.encode | 正确性/安全 | **P1** | `common/protocol/FrameCodec.java:37-39,68` | 缓冲区容量估算+min 截断，边界帧溢出误判 |
| R-06 | PurpurFlowPlugin 未接线会话 | 正确性/可用 | **P0** | `server/PurpurFlowPlugin.java:22-44`（无 PlayerJoinEvent） | 会话永不创建，增强流不可用 |
| R-07 | egress 组件未编排 | 性能/可用 | **P1** | `InboundDispatcher.java:81-86` 直接 sendControl | H3 背压/限速/合批/分片未在发送链生效 |
| R-08 | PriorityQueue 死代码+O(n) 替换 | 性能 | P2 | `server/egress/PriorityQueue.java:25-30` | 队列满时线性扫描 |
| R-09 | Golden 向量未冻结 | 可维护性 | P2 | `test/golden/frame_vectors.md:3-4` | 协议漂移无法被自动捕获 |
| R-10 | test 复制 split 逻辑 | 可维护性 | P2 | `ProtocolTest.java:119-131` | 测试与真实 Fragmenter 可能不一致 |
| R-11 | bench 口径失真 | 可观测性 | P1 | `test/bench/bench.py:22-28,41-47` | 有效载荷比 >1，指标不可信 |
| R-12 | Handshake 会话表无逐帧清理 | 内存 | P2 | `server/session/Handshake.java:19,66` | close 依赖外部调用，断线未清理则 Map 增长 |
| R-13 | 无 Gradle Wrapper / CI | 构建/交付 | P1 | 仓库根（缺失） | 读者无法一键构建，无自动化质量门 |
| R-14 | docs/01 registerLarge IntSupplier 声明 | 文档准确性 | P2 | `docs/01-事实核实.md:108` | 与锁定 Fabric API 0.139.4 不符 |

## 5. 死代码 / 重复 / 隐式耦合

- **【已证实】死代码**：`PriorityQueue.java:25` `Entry lowest = queue.peek()` 未使用；`ProtocolWhitelist.select` 第一循环的 `accepts(v)` 与 `CapabilityNegotiator.negotiate` 的 `whitelist.accepts(cv)` 重复判断。
- **【已证实】重复实现**：`Fragmenter.split`（server）与 `ProtocolTest.FragmenterProbe.split`（test）两份相同逻辑；`CapabilityProbe.localCapability` 与 `Outbound.hello` 各自定义相同的 feature 位图。
- **【工程推断】隐式耦合**：`InboundDispatcher` 构造函数内直接设置 `sender` lambda 依赖 `plugin`，又暴露 `setSender` 可覆盖，存在两套发送路径；客户端 `onJoin/onDisconnect` 是 public 方法但无事件注册，需外部调用（当前无人调用）。

## 6. 全局可变状态

- `Handshake.sessions`（ConcurrentHashMap）：按玩家会话，生命周期由 open/close 管理，close 调用点缺失（R-12）。
- `SnapshotStore.store`（HashMap）：无容量上限，仅靠 `evictExpired` 按 TTL 清理，但 `evictExpired` 无调用点（【已证实】全仓库无引用）→ 内存泄漏风险。
- `DictRegistry.history`（LinkedHashMap）：无清理，版本频繁升级会增长（实际版本变更低频，风险低）。

> 所有风险将在第 2 阶段按优先级闭环：先修 P0（R-01/R-02/R-06），再修 P1（R-03/R-04/R-05/R-07/R-11/R-13），P2 视情况处理。
