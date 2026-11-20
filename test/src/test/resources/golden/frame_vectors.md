# 编解码黄金向量（防止协议漂移）

> 每条向量由 `FrameCodec.encode` 生成，解码应还原相同字段。十六进制字节串为**示意基线**，
> 首次落地时以 `FrameCodec` 实际输出为准生成并冻结；任何变更都须显式更新本文件并说明原因。

## 向量 1：单 HEARTBEAT 帧

- 输入：Frame.data(session=1, seq=1, ack=0, dictVer=1, flags=0, [HEARTBEAT, {0x01,0x02,0x03}])
- 字节（hex，示意）：`50 46 01 00 01 00 00 01 05 01 03 01 02 03`

## 向量 2：多消息合包帧

- 输入：3 条消息（HEARTBEAT / DELTA / RAW）
- 断言：decode 后 messages 顺序与内容一致（见 ProtocolTest.frameCodecRoundTripMultiMessage）

## 向量 3：带 CRC 帧

- 断言：篡改尾字节后 decodeFrame 抛 FrameException（见 ProtocolTest.crcDetectsCorruption）

## 向量 4：VarInt 边界

| 值 | VarInt 字节（hex） |
|---|---|
| 0 | `00` |
| 1 | `01` |
| 127 | `7F` |
| 128 | `80 01` |
| 255 | `FF 01` |
| 256 | `80 02` |

## 向量 5：ZigZag

| 原始 | ZigZag 编码 |
|---|---|
| 0 | 0 |
| -1 | 1 |
| 1 | 2 |
| -2 | 3 |
| 2 | 4 |
