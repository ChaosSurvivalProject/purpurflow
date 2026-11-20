package com.purpurflow.test;

import com.purpurflow.common.control.*;
import com.purpurflow.common.dict.*;
import com.purpurflow.common.protocol.*;
import com.purpurflow.common.state.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第 2 阶段 Bug 修复回归测试。每个测试名带 BUG 编号。
 * 修复前的失败行为在 02_bugfix.md 中记录；本文件锁定修复后的正确行为。
 */
class BugfixRegressionTest {

    // ---------- BUG-C: DictSync 超长字符串缓冲区溢出 ----------
    @Test
    void BUG_C_dictSyncLongStringRoundTrip() {
        // 远超过旧实现假设的 16 字节/项
        String longKey = "quest.daily.login.reward.vip.season.four.".repeat(8); // >300 字节
        DictSync.Payload p = new DictSync.Payload(1, List.of("short", longKey, "x".repeat(1000)));
        byte[] encoded = DictSync.encode(p);
        DictSync.Payload decoded = DictSync.decode(encoded);
        assertEquals(p.fromVersion(), decoded.fromVersion());
        assertEquals(p.additions(), decoded.additions(), "超长字典项必须完整往返");
    }

    // ---------- BUG-E2: HelloAck enhanced 标志丢失 ----------
    @Test
    void BUG_E2_helloAckEnhancedFlagPreserved() {
        HelloAck ack = new HelloAck(1, 0x0102030405L, 7, 32768, true);
        byte[] encoded = ControlFrame.encodeHelloAck(ack);
        HelloAck decoded = ControlFrame.decodeHelloAck(encoded);
        assertEquals(ack.selectedVersion(), decoded.selectedVersion());
        assertEquals(ack.sessionId(), decoded.sessionId());
        assertEquals(ack.dictVersion(), decoded.dictVersion());
        assertEquals(ack.serverMaxChunk(), decoded.serverMaxChunk());
        assertTrue(decoded.enhanced(), "enhanced=true 必须被保留");

        HelloAck ack2 = new HelloAck(1, 42L, 1, 1024, false);
        HelloAck decoded2 = ControlFrame.decodeHelloAck(ControlFrame.encodeHelloAck(ack2));
        assertFalse(decoded2.enhanced(), "enhanced=false 必须被保留");
    }

    // ---------- BUG-E: FrameCodec 精确容量与上限拒绝 ----------
    @Test
    void BUG_E_frameCodecLargePayloadNoOverflow() {
        FrameCodec codec = new FrameCodec();
        // 单条接近 1 MiB 的消息，旧实现 capacity=48+12+len+sizeOf 能容纳，
        // 但多条+最大 varint 头部的边界组合需验证不误抛 BufferOverflow。
        byte[] payload = new byte[700_000];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i & 0x7F);
        Frame f = Frame.data(1L, 1L, 0L, 1, 0, List.of(new Message(FrameType.RAW, payload)));
        byte[] encoded = codec.encode(f);
        Frame decoded = codec.decodeFrame(encoded);
        assertArrayEquals(payload, decoded.messages().get(0).payload(), "大载荷必须完整往返");
    }

    @Test
    void BUG_E_frameCodecOversizeRejectedCleanly() {
        FrameCodec codec = new FrameCodec();
        codec.setLimits(1024, 1024);
        byte[] payload = new byte[2000]; // 超过 maxMessageBytes
        Frame f = Frame.data(1L, 1L, 0L, 1, 0, List.of(new Message(FrameType.RAW, payload)));
        assertThrows(FrameException.class, () -> codec.encode(f), "超上限帧必须抛 FrameException 而非 BufferOverflow");
    }

    // ---------- BUG-A: 字节布局对称性（common 层可验证的部分） ----------
    @Test
    void BUG_A_frameCodecRawBytesSymmetric() {
        FrameCodec codec = new FrameCodec();
        Frame f = Frame.data(0L, 5L, 0L, 1, Flags.RELIABLE_ORDERED | Flags.PRI_CONTROL,
                List.of(new Message(FrameType.HELLO, new byte[]{1, 2, 3, 4, 5})));
        byte[] wire = codec.encode(f);
        // 服务端收到的是裸字节流（无长度前缀），decodeFrame 应可直接解析
        Frame decoded = codec.decodeFrame(wire);
        assertEquals(5L, decoded.seq());
        assertEquals(FrameType.HELLO, decoded.messages().get(0).type());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, decoded.messages().get(0).payload());
    }

    // ---------- BUG-G: SnapshotStore 幂等与 last-write-wins ----------
    @Test
    void BUG_G_snapshotLastWriteWins() {
        Snapshot s = new Snapshot();
        s.put(0, 100, 5);
        s.put(0, 999, 3); // 旧版本
        assertEquals(100, s.value(0), "低版本不得覆盖高版本");
        s.put(0, 200, 6);
        assertEquals(200, s.value(0));
    }

    // ---------- BUG-H: DedupWindow 窗口外 seq 处理 ----------
    @Test
    void BUG_H_dedupWindowAdvances() {
        DedupWindow w = new DedupWindow(4);
        assertTrue(w.isNew(10));
        assertTrue(w.isNew(11));
        assertFalse(w.isNew(10));
        // 窗口推进后旧 seq 应被淘汰
        assertTrue(w.isNew(12));
        assertTrue(w.isNew(13));
        assertTrue(w.isNew(14));
        assertTrue(w.isNew(15));
        assertTrue(w.isNew(16));
        assertTrue(w.size() <= 4, "去重窗口必须有上界, 实际=" + w.size());
    }
}
