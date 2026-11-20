package com.purpurflow.test;

import com.purpurflow.common.protocol.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** R-09：把 golden/frame_vectors.md 的冻结字节变成可执行断言，防止协议静默漂移。 */
class GoldenVectorTest {

    private static byte[] hex(String s) {
        String[] p = s.trim().split("\\s+");
        byte[] b = new byte[p.length];
        for (int i = 0; i < p.length; i++) b[i] = (byte) Integer.parseInt(p[i], 16);
        return b;
    }

    @Test
    void BUG_R09_vector1HeartbeatFrozen() {
        FrameCodec c = new FrameCodec();
        byte[] wire = c.encode(Frame.data(1L, 1L, 0L, 1, 0,
                List.of(new Message(FrameType.HEARTBEAT, new byte[]{1, 2, 3}))));
        assertArrayEquals(hex("50 46 01 00 00 00 00 01 01 00 00 01 01 05 03 01 02 03"), wire,
                "向量1 字节不得漂移");
        Frame d = c.decodeFrame(wire);
        assertEquals(1L, d.session());
        assertEquals(FrameType.HEARTBEAT, d.messages().get(0).type());
    }

    @Test
    void BUG_R09_vector2MultiMessageFrozen() {
        FrameCodec c = new FrameCodec();
        byte[] wire = c.encode(Frame.data(42L, 7L, 6L, 1, 0, List.of(
                new Message(FrameType.HEARTBEAT, new byte[]{1, 2, 3}),
                new Message(FrameType.DELTA, new byte[]{9, 8}),
                new Message(FrameType.RAW, new byte[]{}))));
        assertArrayEquals(hex("50 46 01 00 00 00 00 2A 07 06 00 01 03 05 03 01 02 03 08 02 09 08 20 00"), wire,
                "向量2 多消息合包字节不得漂移");
    }

    @Test
    void BUG_R09_vector3CrcFrozen() {
        FrameCodec c = new FrameCodec();
        byte[] wire = c.encode(Frame.data(1L, 1L, 0L, 1, Flags.CRC,
                List.of(new Message(FrameType.HEARTBEAT, new byte[]{1, 2}))));
        assertArrayEquals(hex("50 46 01 08 00 00 00 01 01 00 00 01 01 05 02 01 02 B9 7F A6 AD"), wire,
                "向量3 带 CRC 字节不得漂移");
        byte[] tampered = wire.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertThrows(FrameException.class, () -> c.decodeFrame(tampered));
    }

    @Test
    void BUG_R09_varintBoundariesFrozen() {
        int[] vals = {0, 1, 127, 128, 255, 256};
        String[] expected = {"00", "01", "7F", "80 01", "FF 01", "80 02"};
        for (int i = 0; i < vals.length; i++) {
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(10);
            VarInts.writeVarInt(b, vals[i]);
            byte[] got = java.util.Arrays.copyOf(b.array(), b.position());
            assertArrayEquals(hex(expected[i]), got, "VarInt(" + vals[i] + ") 字节不得漂移");
        }
    }
}
