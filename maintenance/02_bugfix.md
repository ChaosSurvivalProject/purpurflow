# 02 · Bug 修复（第 2 阶段）

> 自检元数据｜执行模型：Tencent Hunyuan Hy（用户 2026-11-21 授权接手；ChatGPT API key 失效，见文末降级声明）｜推理档位：当前环境最高可用｜本阶段是否发生降级：是（模型替换，非能力降级；已获用户明确授权）｜仓库 commit：`6d525f694bc0592abd9f6ffbf0cbb1377b9a1db0`

> **验证方式**：本环境 JDK21 下载受阻、无 Gradle Wrapper，但 common/server/client 全部源码为可编译 Java。我建立了一套**最小 API 桩（stub）**覆盖 Bukkit/Fabric/Minecraft 网络类型，用 `javac` 完整编译全部 68 个 class，并用 JUnit 独立运行器真实执行测试。所有"通过"均为**实际运行**结果，非静态推断。

---

## 0. 修复前基线（真实运行）

```
编译：common 23 文件 javac 通过；test 编译通过
JUnit：16 tests found / 14 successful / 2 failed
  失败① BUG_C_dictSyncLongStringRoundTrip → BufferOverflowException
  失败② BUG_E_frameCodecOversizeRejectedCleanly → 期望 FrameException，实际 BufferOverflowException
```

## 1. 已修复 Bug 清单（复现 → 根因 → 修复 → 回归 → 前后对比）

### R-03 / BUG-E2 · HELLO_ACK 的 enhanced 标志恒为 false（P1，兼容性）
- **复现**：`HelloAck(enhanced=true)` → `encodeHelloAck` → `decodeHelloAck` → 得到 `enhanced=false`。回归用例 `BUG_E2_helloAckEnhancedFlagPreserved`。
- **根因（到行）**：旧 `decodeHelloAck` 用 `bytes.length > 16` 判断 enhanced，但编码器固定写 16 字节（1+8+2+4+1），条件恒 false；第 16 字节（索引 15）的 enhanced 位从未被读取。
- **修复**：编码固定 16 字节；解码改读第 16 字节 `b.get() == 1`。见 `ControlFrame.java:29-49`。
- **前后对比**：修复前 enhanced 恒 false → 客户端收到后误判"服务端拒绝"降级 vanilla（`Inbound.java:39`），增强流对所有客户端失效；修复后 true/false 均正确保留。
- **回归**：`BUG_E2_helloAckEnhancedFlagPreserved` ✔（含 true/false 双向）。

### R-04 / BUG-C · DictSync 超长字典项缓冲区溢出（P1，正确性/安全）
- **复现**：`DictSync.encode(Payload(1, [300 字节键, 1000 字节值]))` → `BufferOverflowException`。回归用例 `BUG_C_dictSyncLongStringRoundTrip`（基线失败项①）。
- **根因（到行）**：`DictSync.java:14` 旧 `cap = 4 + size*16`，假设每项 ≤16 字节；真实键长可达数百字节，`buf.put(b)` 越界。
- **修复**：按每项真实 UTF-8 字节数 + VarInt 前缀长度用 `long` 精确预计算容量，超 `Integer.MAX_VALUE` 显式拒绝。见 `DictSync.java:13-30`。
- **前后对比**：修复前长字典同步必崩；修复后 300B/1000B 项完整往返。
- **回归**：`BUG_C_dictSyncLongStringRoundTrip` ✔（由失败转通过）。

### R-05 / BUG-E · FrameCodec 容量估算与上限误判（P1，正确性/安全）
- **复现**：`setLimits(1024,1024)` + 2000 字节消息 → 期望 `FrameException`，实际 `BufferOverflowException`。回归用例 `BUG_E_frameCodecOversizeRejectedCleanly`（基线失败项②）。
- **根因（到行）**：`FrameCodec.java:37-39` 旧 `capacity = 48 + n*12 + Σ(len+varint)` 后用 `ByteBuffer.allocate(Math.min(capacity, maxFrameBytes))`——`min` 把缓冲区截断到上限，写入超长载荷时在 `buf.put` 抛底层 `BufferOverflowException`，既非可控异常，又可能写出被静默裁剪的损坏帧。
- **修复**：用 `long` 精确累加各字段字节（含变长头），先校验"单条消息 > maxMessageBytes"与"整帧 > maxFrameBytes"并抛 `FrameException`，再用精确容量分配，去掉 `Math.min` 截断。见 `FrameCodec.java:33-52`。
- **前后对比**：修复前超限输入抛不可控异常/潜在损坏帧；修复后统一抛 `FrameException`，且 700KB 合法大帧完整往返（用例 `BUG_E_frameCodecLargePayloadNoOverflow` ✔）。
- **回归**：2 个 BUG_E 用例 ✔。

### R-02 / BUG-B · 多分片重组必然失败（P0，正确性）
- **复现**：4 片顺序/乱序到达，`Reassembler.accept` 恒返回 null，永不收齐。回归用例 `ReassemblerRegressionTest`（4 个）。
- **根因（到行）**：`Reassembler.java:39,43,45` 旧 `total = more ? index+1 : 1`——首片 index=0 时 total=1，`Part(1)` 仅分配 1 个槽；第二片 index=1 触发 `index >= chunks.length` 被丢弃。任意 >2 片无法收齐，且不支持乱序。
- **修复**：改为按 index 索引的 `Map<Integer,byte[]>`；由"不带 MORE 的最后片"确定总数 `expectedTotal`；支持乱序到达；重复片去重；按 index 升序拼接；加 `MAX_FRAGMENTS=65536` 上界防恶意 index 撑爆内存。见 `Reassembler.java:14-88`。
- **前后对比**：修复前 2 片以上无法重组；修复后 4 片顺序/乱序、重复片、越界片全部正确。
- **回归**：`BUG_B_fourFragmentsInOrder` / `BUG_B_outOfOrderFragments` / `BUG_B_duplicateFragmentIgnored` / `BUG_B_outOfRangeIndexDropped` ✔。

### R-06 / BUG-F · 会话永不创建，增强流完全不可用（P0，功能）
- **复现**：静态全仓搜索确认 `handshake.open()` 无调用点；`InboundDispatcher:38` `session==null` 直接 return。回归用例 `SessionLifecycleRegressionTest`（4 个）。
- **根因（到行）**：`PurpurFlowPlugin.onEnable`（旧 22-44 行）只注册了通道，未注册玩家生命周期监听，会话表恒空。
- **修复**：新增 `SessionLifecycleListener`，`PlayerJoinEvent` → `handshake.open(uuid, sessionId, epoch)`，`PlayerQuitEvent` → `handshake.close(uuid)`；新增 `Handshake.expireAll()` 与定时巡检；`sessionCount()` 供观测。见 `PurpurFlowPlugin.java`、`Handshake.java:57-84`。
- **前后对比**：修复前无任何会话，握手无从发起；修复后进服即建会话、HELLO 可协商为 ENHANCED、不兼容版本安全降级 VANILLA、超时批量降级、断线清理。
- **回归**：4 个 BUG_F 用例 ✔（含 last-write-wins 联动、vanilla 降级 H5、超时、清理 R-12）。

### R-07 / BUG-G · H3 背压/限速/队列/优先级"零件齐全未装车"（P1，性能/可用）
- **复现**：旧 `InboundDispatcher.sendControl:81-86` 直接 `sender.send(...)`，完全绕过 egress 组件。回归用例 `EgressPipelineRegressionTest`（4 个）。
- **根因（到行）**：`egress/` 下 6 个组件（PriorityQueue/RateLimiter/BackpressureGate/Batcher/Fragmenter/EgressGate）已实现但主链路零引用。
- **修复**：新增 `EgressPipeline` 编排器，把优先级队列 + 速率令牌桶 + 可写性门控串成一条链（每玩家独立实例）；`InboundDispatcher` 注入配置、控制帧入队、`drain(player,maxFrames)` 排空；插件定时 tick 调 drain；quit 清理。`sendControl` 保留 `egressEnabled=false` 直发作为一键回滚（H2）。见 `EgressPipeline.java`、`InboundDispatcher.java`、`PurpurFlowPlugin.java`。
- **前后对比**：修复前高压下无背压/限速，弱网可能雪崩；修复后队满按优先级丢弃、不可写背压丢弃、限速排队不丢帧、控制帧优先出队。
- **回归**：4 个 BUG_G 用例 ✔。

### R-08 · PriorityQueue 队满替换逻辑死代码（P2，可维护性）
- **根因**：`PriorityQueue.java:25` `Entry lowest = queue.peek()` 未被使用（最小堆 peek 返回最高优先级，与"找最低优先级替换"语义无关，属误导性死代码）。
- **修复**：删除该行，保留 `stream().max(优先级, order)` 找最低优先级最旧元素替换；补注释。见 `PriorityQueue.java:23-34`。
- **回归**：EgressPipeline 的 `queueBoundedAndLowPriorityDropped` 覆盖高优先级替换 ✔。

---

## 2. 需实测验证（不臆断、不乱改）

### R-01 · FlowPayload.CODEC 字节布局"疑似不对称"（P0，跨 API 对接）
- **现象**：`FlowPayload.java:22-23` 编码用 `buf.writeBytes(data)`（Netty 裸字节，**无**长度前缀），解码用 `buf.readByteArray()`（FriendlyByteBuf **读 VarInt 长度前缀 + N 字节**）。表面看收发不对称。
- **但不能直接判定为 bug**：本项目是 Bukkit 服务端 + Fabric 客户端跨 API 对接的骨架。
  - S2C：服务端用 Bukkit `Player.sendPluginMessage`，vanilla 协议对 plugin-message 的 data 部分本身带 VarInt 长度前缀；Fabric 客户端 `CustomPayload` 解码时该前缀由 vanilla 层处理——`readByteArray()` 是否恰好对应，取决于 Fabric 1.21.11 网络层给 `StreamCodec` 的 buffer 是否已剥离前缀。
  - C2S：Fabric `CustomPacketPayload` 由包边界界定长度，`writeBytes` 裸写可能是有意为之。
- **结论**：编码/解码是否真不对称，**必须在真实 Purpur 1.21.11 + Fabric 1.21.11 双端抓包验证**，不能凭静态阅读改。若盲改任一侧，可能反而破坏现有 vanilla 对接。**保持原样，标 `需实测验证`**，列入第 6 阶段试机必测项（握手 HELLO 往返是否成功即可证伪/证实）。
- **验证所需条件**：可启动的 Purpur 1.21.11 服务端 + 装 Mod 的 Fabric 客户端（或 headless 集成客户端）+ 抓包/日志。

### 其余 P2（本阶段未改，登记待办）
- R-09 Golden 向量未冻结（向量 1 字节串标"示意"）→ 第 4 阶段冻结真实输出。
- R-10 test 内 `FragmenterProbe` 复制 split 逻辑 → 第 3 阶段改为直接复用 server `Fragmenter`（纯 Java，可解耦）。
- R-11 `bench.py` 有效载荷比 >1 口径失真 → 第 3 阶段重写。
- R-13 无 Gradle Wrapper / CI → 第 4 阶段补。
- R-14 `docs/01` 声称 `registerLarge` 支持 `IntSupplier` 动态分包，与锁定 Fabric API 0.139.4 只有 `int` 重载不符 → 第 5 阶段修文档。

---

## 3. 修复后基线（真实运行）

```
全模块编译：common+server+client+stub 共 68 class，javac EXIT=0
JUnit：8 containers / 28 tests found / 28 successful / 0 failed
  覆盖：ProtocolTest(9) + BugfixRegression(7) + Reassembler(4) + SessionLifecycle(4) + EgressPipeline(4)
```

## 4. 降级声明（诚实性）
- 用户上传提示词要求"全程使用 ChatGPT 最高能力模型"。执行中该 ChatGPT API key（`api.moxcloud.top`）于 2026-11-21 00:42 起对所有端点/模型返回 **401 Invalid API key**（截图注明"1H 后发调用 key"，key 已过期）。
- 用户随后明确授权"先用你自己的模型吧，仔细一点"。故本阶段及后续由 Tencent Hunyuan Hy 执行，已获授权，非静默降级。
- 所有测试/编译/指标均为本环境真实执行结果，未伪造。
