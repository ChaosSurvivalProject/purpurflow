package com.purpurflow.common.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * 帧编解码器（L1）：一帧多消息合包 + 分片重组支撑 + 可选 CRC。
 * 契约：encode(List&lt;Message&gt;) -&gt; byte[]，decode(byte[]) -&gt; List&lt;Message&gt;。
 * 本类零 MC 依赖，纯字节操作，可在网络线程调用，不产生阻塞。
 */
public final class FrameCodec {

    /** 默认上限，可通过 setLimits 按运行时探测值覆盖（不硬编码）。 */
    private volatile int maxFrameBytes = 1_048_576;   // 下行 1 MiB
    private volatile int maxMessageBytes = 1_048_576;
    private volatile boolean verifyCrc = true;

    public void setLimits(int maxFrameBytes, int maxMessageBytes) {
        if (maxFrameBytes <= 0 || maxMessageBytes <= 0) {
            throw new IllegalArgumentException("上限必须为正数");
        }
        this.maxFrameBytes = maxFrameBytes;
        this.maxMessageBytes = maxMessageBytes;
    }

    public void setVerifyCrc(boolean verifyCrc) {
        this.verifyCrc = verifyCrc;
    }

    /** 编码一帧（携带完整头部）。 */
    public byte[] encode(Frame frame) {
        if (frame.messages().size() > 0xFFFF) throw new FrameException("单帧消息数过多");

        // 头部最多约 40B（magic/ver/flags/session/seq/ack/dict/frag/count）+ CRC 4B
        int capacity = 48 + frame.messages().size() * 12;
        for (Message m : frame.messages()) capacity += m.payload().length + VarInts.sizeOfVarInt(m.payload().length);
        ByteBuffer buf = ByteBuffer.allocate(Math.min(capacity, maxFrameBytes));

        buf.put((byte) Frame.MAGIC0);
        buf.put((byte) Frame.MAGIC1);
        buf.put((byte) frame.ver());
        buf.put((byte) frame.flags());
        buf.putInt((int) frame.session());          // u32
        VarInts.writeVarLong(buf, frame.seq());
        VarInts.writeVarLong(buf, frame.ack());
        buf.putShort((short) frame.dictVer());      // u16
        if (frame.isFragment()) {
            VarInts.writeVarInt(buf, Math.max(0, frame.fragIndex()));
        }
        VarInts.writeVarInt(buf, frame.messages().size());
        for (Message m : frame.messages()) {
            VarInts.writeVarInt(buf, m.type().id());
            VarInts.writeVarInt(buf, m.payload().length);
            buf.put(m.payload());
        }

        if (Flags.has(frame.flags(), Flags.CRC)) {
            CRC32 crc = new CRC32();
            crc.update(buf.array(), 0, buf.position());
            buf.putInt((int) crc.getValue());
        }

        byte[] out = new byte[buf.position()];
        buf.flip();
        buf.get(out);
        if (out.length > maxFrameBytes) throw new FrameException("帧超过上限: " + out.length);
        return out;
    }

    /** 契约方法：把消息列表编码为一帧（默认 session/seq/ack/dictVer=0，非分片）。 */
    public byte[] encode(List<Message> messages) {
        return encode(Frame.data(0L, 0L, 0L, 0, 0, messages));
    }

    /** 解码为完整 Frame（含头部），供控制面使用。 */
    public Frame decodeFrame(byte[] bytes) {
        if (bytes == null || bytes.length < 8) throw new FrameException("帧过短");
        if (bytes.length > maxFrameBytes) throw new FrameException("帧超过上限: " + bytes.length);

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        if ((buf.get() & 0xFF) != Frame.MAGIC0 || (buf.get() & 0xFF) != Frame.MAGIC1) {
            throw new FrameException("magic 不匹配");
        }
        int ver = buf.get() & 0xFF;
        if (ver != Frame.CURRENT_VERSION) throw new FrameException("协议版本不受支持: " + ver);
        int flags = buf.get() & 0xFF;
        long session = Integer.toUnsignedLong(buf.getInt());
        long seq = VarInts.readVarLong(buf);
        long ack = VarInts.readVarLong(buf);
        int dictVer = buf.getShort() & 0xFFFF;
        int fragIndex = Flags.has(flags, Flags.FRAG) ? VarInts.readVarInt(buf) : -1;

        if (Flags.has(flags, Flags.CRC) && verifyCrc) {
            int payloadLen = bytes.length - 4;
            CRC32 crc = new CRC32();
            crc.update(bytes, 0, payloadLen);
            int expected = ByteBuffer.wrap(bytes, payloadLen, 4).getInt();
            if ((int) crc.getValue() != expected) throw new FrameException("CRC 校验失败");
        }

        int count = VarInts.readVarInt(buf);
        if (count < 0 || count > 0xFFFF) throw new FrameException("消息数非法: " + count);
        List<Message> messages = new ArrayList<>(Math.min(count, 1024));
        int limit = Flags.has(flags, Flags.CRC) ? bytes.length - 4 : bytes.length;
        for (int i = 0; i < count; i++) {
            int typeId = VarInts.readVarInt(buf);
            int len = VarInts.readVarInt(buf);
            if (len < 0 || len > maxMessageBytes) throw new FrameException("消息长度非法: " + len);
            if (buf.position() + len > limit) throw new FrameException("消息越界");
            byte[] payload = new byte[len];
            buf.get(payload);
            messages.add(new Message(FrameType.fromId(typeId), payload));
        }
        return new Frame(ver, flags, session, seq, ack, dictVer, fragIndex, messages);
    }

    /** 契约方法：解码为消息列表。 */
    public List<Message> decode(byte[] bytes) {
        return decodeFrame(bytes).messages();
    }
}
