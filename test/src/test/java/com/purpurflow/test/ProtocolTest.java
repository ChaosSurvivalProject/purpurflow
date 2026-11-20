package com.purpurflow.test;

import com.purpurflow.common.protocol.*;
import com.purpurflow.common.state.*;
import com.purpurflow.common.dict.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 协议单测：VarInt/ZigZag 边界、编解码往返、合包、分片、delta、脏位图、去重、字典回滚。 */
class ProtocolTest {

    @Test
    void varIntRoundTrip() {
        int[] values = {0, 1, 127, 128, 255, 256, 1_000_000, Integer.MAX_VALUE, -1, Integer.MIN_VALUE};
        for (int v : values) {
            ByteBuffer b = ByteBuffer.allocate(10);
            VarInts.writeVarInt(b, v);
            b.flip();
            assertEquals(v, VarInts.readVarInt(b), "VarInt 往返 v=" + v);
        }
    }

    @Test
    void zigZagRoundTrip() {
        int[] vals = {0, -1, 1, -2, 2, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int v : vals) assertEquals(v, VarInts.zigZagDecode(VarInts.zigZagEncode(v)));
        long[] longs = {0L, -1L, 1L, Long.MAX_VALUE, Long.MIN_VALUE};
        for (long v : longs) assertEquals(v, VarInts.zigZagDecode(VarInts.zigZagEncode(v)));
    }

    @Test
    void frameCodecRoundTripMultiMessage() {
        FrameCodec codec = new FrameCodec();
        List<Message> msgs = List.of(
                new Message(FrameType.HEARTBEAT, new byte[]{1, 2, 3}),
                new Message(FrameType.DELTA, new byte[]{9, 8}),
                new Message(FrameType.RAW, new byte[]{})
        );
        byte[] encoded = codec.encode(Frame.data(42L, 7L, 6L, 1, 0, msgs));
        Frame decoded = codec.decodeFrame(encoded);
        assertEquals(42L, decoded.session());
        assertEquals(7L, decoded.seq());
        assertEquals(6L, decoded.ack());
        assertEquals(msgs, decoded.messages());
    }

    @Test
    void crcDetectsCorruption() {
        FrameCodec codec = new FrameCodec();
        byte[] encoded = codec.encode(Frame.data(1L, 1L, 0L, 1, Flags.CRC,
                List.of(new Message(FrameType.HEARTBEAT, new byte[]{1, 2}))));
        encoded[encoded.length - 1] ^= 0x01; // 翻转 CRC 末字节
        assertThrows(FrameException.class, () -> codec.decodeFrame(encoded));
    }

    @Test
    void magicAndVersionRejected() {
        FrameCodec codec = new FrameCodec();
        byte[] good = codec.encode(Frame.data(1, 1, 0, 1, 0, List.of()));
        good[0] = 0x00; // 破坏 magic
        assertThrows(FrameException.class, () -> codec.decodeFrame(good));
    }

    @Test
    void fragmentSplitCoversAllBytes() {
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        FragmenterProbe.Fragment[] parts = FragmenterProbe.split(data, 32);
        assertEquals(4, parts.length);
        int off = 0;
        for (FragmenterProbe.Fragment p : parts) {
            assertArrayEquals(java.util.Arrays.copyOfRange(data, off, off + p.data().length), p.data());
            off += p.data().length;
        }
        assertEquals(100, off);
    }

    @Test
    void deltaDirtyBitmapAndIdempotentApply() {
        Snapshot snap = new Snapshot();
        Delta d = new Delta(5L);
        d.put(0, 100, 1);
        d.put(3, 200, 1);
        assertEquals(2, d.dirty().count());
        snap.apply(d);
        assertEquals(100, snap.value(0));
        assertEquals(200, snap.value(3));
        // 重复应用（幂等，last-write-wins 不回退）
        d.put(0, 50, 0); // 旧版本
        snap.apply(d);
        assertEquals(100, snap.value(0), "旧版本不得覆盖新值");
    }

    @Test
    void dedupWindowRejectsDuplicates() {
        DedupWindow w = new DedupWindow(8);
        assertTrue(w.isNew(1));
        assertFalse(w.isNew(1));
        assertTrue(w.isNew(2));
        assertFalse(w.isNew(2));
    }

    @Test
    void dictionaryDeltaAndRollback() {
        DictSnapshot v1 = new DictSnapshot(1, List.of("a", "b"));
        DictRegistry reg = new DictRegistry(v1);
        DictSnapshot v2 = v1.applyDelta(List.of("c", "d"));
        reg.install(v2);
        assertEquals(2, reg.idOf("c"));
        assertEquals("d", reg.stringOf(3));
        assertTrue(reg.rollbackTo(1));
        assertEquals(1, reg.currentVersion());
    }

    /** 分片探测（与 server Fragmenter 同逻辑，避免测试依赖 Bukkit）。 */
    static final class FragmenterProbe {
        record Fragment(byte[] data, int index, boolean more) {}
        static Fragment[] split(byte[] data, int chunk) {
            int count = (data.length + chunk - 1) / chunk;
            Fragment[] out = new Fragment[count];
            for (int i = 0; i < count; i++) {
                int from = i * chunk, to = Math.min(data.length, from + chunk);
                out[i] = new Fragment(java.util.Arrays.copyOfRange(data, from, to), i, i < count - 1);
            }
            return out;
        }
    }
}
