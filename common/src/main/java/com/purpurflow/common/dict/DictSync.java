package com.purpurflow.common.dict;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** DICT_SYNC 消息的载荷编解码：fromVer(u16) + count(varint) + (len(varint)+bytes)*。 */
public final class DictSync {
    private DictSync() {}

    public record Payload(int fromVersion, List<String> additions) {}

    public static byte[] encode(Payload p) {
        int cap = 4 + p.additions().size() * 16;
        ByteBuffer buf = ByteBuffer.allocate(cap);
        buf.putShort((short) p.fromVersion());
        com.purpurflow.common.protocol.VarInts.writeVarInt(buf, p.additions().size());
        for (String s : p.additions()) {
            byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            com.purpurflow.common.protocol.VarInts.writeVarInt(buf, b.length);
            buf.put(b);
        }
        return java.util.Arrays.copyOf(buf.array(), buf.position());
    }

    public static Payload decode(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        int fromVersion = buf.getShort() & 0xFFFF;
        int count = com.purpurflow.common.protocol.VarInts.readVarInt(buf);
        if (count < 0 || count > 0xFFFF) throw new IllegalArgumentException("字典增量条数非法");
        List<String> additions = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            int len = com.purpurflow.common.protocol.VarInts.readVarInt(buf);
            if (len < 0 || len > buf.remaining()) throw new IllegalArgumentException("字典字符串长度非法");
            byte[] b = new byte[len];
            buf.get(b);
            additions.add(new String(b, java.nio.charset.StandardCharsets.UTF_8));
        }
        return new Payload(fromVersion, additions);
    }
}
