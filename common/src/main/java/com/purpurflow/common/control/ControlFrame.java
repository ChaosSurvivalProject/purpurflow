package com.purpurflow.common.control;

import java.nio.ByteBuffer;

/** 控制面消息载荷编解码（HELLO/HELLO_ACK/RESUME/HEARTBEAT）。白名单固定 schema。 */
public final class ControlFrame {
    private ControlFrame() {}

    public static byte[] encodeHello(Hello h) {
        Capability c = h.capability();
        ByteBuffer b = ByteBuffer.allocate(14);
        b.put((byte) c.protocolVersion());
        b.putShort((short) c.dictVersion());
        b.putInt(c.featureBits());
        b.putInt(c.localMaxChunk());
        return java.util.Arrays.copyOf(b.array(), b.position());
    }

    public static Hello decodeHello(byte[] bytes) {
        if (bytes.length < 11) throw new IllegalArgumentException("HELLO 载荷过短");
        ByteBuffer b = ByteBuffer.wrap(bytes);
        int ver = b.get() & 0xFF;
        int dict = b.getShort() & 0xFFFF;
        int features = b.getInt();
        int chunk = b.getInt();
        return new Hello(new Capability(ver, dict, features, chunk));
    }

    public static byte[] encodeHelloAck(HelloAck a) {
        // 固定 16 字节：ver(1) + session(8) + dict(2) + chunk(4) + enhanced(1)。
        ByteBuffer b = ByteBuffer.allocate(16);
        b.put((byte) a.selectedVersion());
        b.putLong(a.sessionId());
        b.putShort((short) a.dictVersion());
        b.putInt(a.serverMaxChunk());
        b.put((byte) (a.enhanced() ? 1 : 0));
        return b.array();
    }

    public static HelloAck decodeHelloAck(byte[] bytes) {
        if (bytes.length < 16) throw new IllegalArgumentException("HELLO_ACK 载荷过短");
        ByteBuffer b = ByteBuffer.wrap(bytes);
        int ver = b.get() & 0xFF;
        long session = b.getLong();
        int dict = b.getShort() & 0xFFFF;
        int chunk = b.getInt();
        boolean enhanced = b.get() == 1;
        return new HelloAck(ver, session, dict, chunk, enhanced);
    }

    public static byte[] encodeResume(Resume r) {
        ByteBuffer b = ByteBuffer.allocate(24);
        b.putLong(r.sessionId());
        b.putLong(r.epoch());
        b.putLong(r.ackSeq());
        return java.util.Arrays.copyOf(b.array(), b.position());
    }

    public static Resume decodeResume(byte[] bytes) {
        if (bytes.length < 24) throw new IllegalArgumentException("RESUME 载荷过短");
        ByteBuffer b = ByteBuffer.wrap(bytes);
        return new Resume(b.getLong(), b.getLong(), b.getLong());
    }
}
