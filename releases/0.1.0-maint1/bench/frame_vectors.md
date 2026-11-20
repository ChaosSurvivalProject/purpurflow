# 编解码黄金向量（防止协议漂移）

> 每条向量由真实 `FrameCodec.encode` 生成并**已冻结**（commit `6d525f6` 基线 + 第 2/3 阶段修复后）。
> 十六进制字节串来自 `test/bench/GenVectors.java` 的真实输出，非示意。
> 对应自动断言见 `test/src/test/java/com/purpurflow/test/GoldenVectorTest.java`。
> 任何协议变更若改动这些字节，须显式更新本文件与断言并说明原因（H5 版本兼容）。

## 向量 1：单 HEARTBEAT 帧
- 输入：`Frame.data(session=1, seq=1, ack=0, dictVer=1, flags=0, [HEARTBEAT, {01 02 03}])`
- 字节（hex，17B，**冻结**）：`50 46 01 00 00 00 00 01 01 00 00 01 01 05 03 01 02 03`
- 字段解读：magic=`50 46` / ver=`01` / flags=`00` / session(u32)=`00 00 00 01` / seq=`01` / ack=`00` / dictVer=`00 01` / count=`01` / type=`05`(HEARTBEAT) / len=`03` / payload=`01 02 03`

## 向量 2：多消息合包帧
- 输入：`Frame.data(session=42, seq=7, ack=6, dictVer=1, flags=0, [HEARTBEAT{01 02 03}, DELTA{09 08}, RAW{}])`
- 字节（hex，24B，**冻结**）：`50 46 01 00 00 00 00 2A 07 06 00 01 03 05 03 01 02 03 08 02 09 08 20 00`
- 断言：decode 后 3 条消息顺序与内容一致

## 向量 3：带 CRC 帧
- 输入：`Frame.data(session=1, seq=1, ack=0, dictVer=1, flags=CRC, [HEARTBEAT{01 02}])`
- 字节（hex，21B，**冻结**）：`50 46 01 08 00 00 00 01 01 00 00 01 01 05 02 01 02 B9 7F A6 AD`
- 断言：篡改末字节后 `decodeFrame` 抛 `FrameException`

## 向量 4：VarInt 边界（冻结）
| 值 | VarInt 字节（hex） |
|---|---|
| 0 | `00` |
| 1 | `01` |
| 127 | `7F` |
| 128 | `80 01` |
| 255 | `FF 01` |
| 256 | `80 02` |

## 向量 5：ZigZag（冻结）
| 原始 | ZigZag 编码 |
|---|---|
| 0 | 0 |
| -1 | 1 |
| 1 | 2 |
| -2 | 3 |
| 2 | 4 |

## 生成方式（可复现）
```
javac -cp <classes> -d <classes> test/bench/GenVectors.java
java -cp <classes> GenVectors
```
> 注：`GenVectors.java` 位于 `test/bench/`，为生成工具（非 JUnit 测试），未纳入自动测试源集。上方 hex 为其真实输出的人工冻结快照，由 `GoldenVectorTest` 断言守护。
