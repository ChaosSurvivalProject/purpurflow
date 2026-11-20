package com.purpurflow.test;

import com.purpurflow.client.net.Reassembler;
import com.purpurflow.common.protocol.Flags;
import com.purpurflow.common.protocol.Frame;
import com.purpurflow.common.protocol.Message;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** R-02 分片重组回归测试：多片、乱序、重复片、越界片。 */
class ReassemblerRegressionTest {

    private static Frame frag(long session, long seq, int index, boolean more) {
        int flags = Flags.FRAG | (more ? Flags.MORE : 0);
        return new Frame(Frame.CURRENT_VERSION, flags, session, seq, 0L, 1, index, List.of());
    }

    /** 4 片顺序到达——旧实现 Part(1) 会在第 2 片丢弃，永远返回 null。 */
    @Test
    void BUG_B_fourFragmentsInOrder() {
        Reassembler r = new Reassembler(16, 5000);
        byte[] p0 = "AAA".getBytes(StandardCharsets.UTF_8);
        byte[] p1 = "BBBB".getBytes(StandardCharsets.UTF_8);
        byte[] p2 = "CC".getBytes(StandardCharsets.UTF_8);
        byte[] p3 = "DDDDD".getBytes(StandardCharsets.UTF_8);

        assertNull(r.accept(frag(1, 1, 0, true), p0));
        assertNull(r.accept(frag(1, 1, 1, true), p1));
        assertNull(r.accept(frag(1, 1, 2, true), p2));
        byte[] out = r.accept(frag(1, 1, 3, false), p3);
        assertNotNull(out, "4 片必须能收齐");
        assertArrayEquals("AAABBBBCCDDDDD".getBytes(StandardCharsets.UTF_8), out, "拼接顺序须正确");
    }

    /** 乱序到达：最后片先到，再补前序片，仍须按 index 顺序拼接。 */
    @Test
    void BUG_B_outOfOrderFragments() {
        Reassembler r = new Reassembler(16, 5000);
        byte[] p0 = "0".getBytes(StandardCharsets.UTF_8);
        byte[] p1 = "1".getBytes(StandardCharsets.UTF_8);
        byte[] p2 = "2".getBytes(StandardCharsets.UTF_8);

        assertNull(r.accept(frag(2, 9, 2, false), p2)); // 最后片先到
        assertNull(r.accept(frag(2, 9, 1, true), p1));
        byte[] out = r.accept(frag(2, 9, 0, true), p0);
        assertNotNull(out);
        assertArrayEquals("012".getBytes(StandardCharsets.UTF_8), out, "乱序须按 index 归位");
    }

    /** 重复片不得导致提前收齐或字节重复。 */
    @Test
    void BUG_B_duplicateFragmentIgnored() {
        Reassembler r = new Reassembler(16, 5000);
        byte[] p0 = "X".getBytes(StandardCharsets.UTF_8);
        byte[] p1 = "YZ".getBytes(StandardCharsets.UTF_8);

        assertNull(r.accept(frag(3, 4, 0, true), p0));
        assertNull(r.accept(frag(3, 4, 0, true), p0)); // 重复
        byte[] out = r.accept(frag(3, 4, 1, false), p1);
        assertNotNull(out);
        assertArrayEquals("XYZ".getBytes(StandardCharsets.UTF_8), out, "重复片不得写入两次");
    }

    /** 越界 index（>=65536）须被安全丢弃而不抛异常、不撑爆内存。 */
    @Test
    void BUG_B_outOfRangeIndexDropped() {
        Reassembler r = new Reassembler(16, 5000);
        assertNull(r.accept(frag(4, 1, 70000, false), new byte[]{1}));
    }
}
