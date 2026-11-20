package com.purpurflow.common.protocol;

import java.util.List;

/**
 * 一帧：头部字段 + 多条消息。
 * 帧格式：magic(2B) | ver(u8) | flags(u8) | session(u32) | seq(varint) | ack(varint)
 *       | dictVer(u16) | [fragIndex(varint) if FRAG] | count(varint) | (type,len,payload)* | [crc(4B)]
 */
public final class Frame {
    public static final int MAGIC0 = 0x50; // 'P'
    public static final int MAGIC1 = 0x46; // 'F'
    public static final int CURRENT_VERSION = 1;

    private final int ver;
    private final int flags;
    private final long session;
    private final long seq;
    private final long ack;
    private final int dictVer;
    private final int fragIndex;   // -1 表示非分片
    private final List<Message> messages;

    public Frame(int ver, int flags, long session, long seq, long ack, int dictVer,
                 int fragIndex, List<Message> messages) {
        this.ver = ver;
        this.flags = flags;
        this.session = session;
        this.seq = seq;
        this.ack = ack;
        this.dictVer = dictVer;
        this.fragIndex = fragIndex;
        this.messages = List.copyOf(messages);
    }

    public int ver() { return ver; }
    public int flags() { return flags; }
    public long session() { return session; }
    public long seq() { return seq; }
    public long ack() { return ack; }
    public int dictVer() { return dictVer; }
    public int fragIndex() { return fragIndex; }
    public List<Message> messages() { return messages; }

    public boolean isFragment() {
        return Flags.has(flags, Flags.FRAG);
    }

    /** 构造一个普通（非分片）数据帧的便捷工厂。 */
    public static Frame data(long session, long seq, long ack, int dictVer, int flags, List<Message> msgs) {
        return new Frame(CURRENT_VERSION, flags, session, seq, ack, dictVer, -1, msgs);
    }
}
