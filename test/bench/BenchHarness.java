import com.purpurflow.common.protocol.*;
import com.purpurflow.common.state.*;
import com.purpurflow.common.dict.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 真实压测探针：直接调用 FrameCodec / Snapshot / Delta / DictSync 的真实实现，
 * 输出每条逻辑更新的真实编码字节数，杜绝 bench.py 的拍脑袋估算（R-11）。
 *
 * 口径：
 *  - 基线（baseline）：每字段单独一帧，载荷为 JSON 文本 {"field_i":value}，计总字节与包数。
 *  - 优化（optimized）：一次更新的所有变更字段组成一条 Delta，塞进同一帧的 RAW 消息，
 *    用真实 FrameCodec.encode 得到字节数，计总字节与包数（一帧 = 一包）。
 *  - 有效逻辑字节：变更字段的 (varint(key) + zigzag(varint(value))) 真实字节数，
 *    两条路径用同一个定义，payload_ratio 不再 >1。
 *
 * 用法: java -cp <classes> BenchHarness <updates> <fields> <dirtyRatio>
 * 输出：单行 TSV，字段见 main。
 */
public final class BenchHarness {
    static final class Metrics {
        long baselineBytes, optimizedBytes, logicalBytes;
        long baselinePackets, optimizedPackets;
        long baselineNanos, optimizedNanos;
    }

    static int varIntSize(int v) {
        int n = 0;
        do { n++; v >>>= 7; } while (v != 0);
        return n;
    }

    static byte[] deltaPayload(Snapshot snap, int baseSeq, int fields, int dirtyRatio, Random rnd) {
        Delta d = new Delta(baseSeq);
        int dirty = Math.max(1, fields * dirtyRatio / 100);
        for (int k = 0; k < dirty; k++) {
            int key = rnd.nextInt(fields);
            int value = rnd.nextInt(1_000_000);
            d.put(key, value, baseSeq);
            snap.put(key, value, baseSeq);
        }
        // 用 DictSync 风格把 delta 序列化为紧凑字节：此处的真实载荷编码
        // = 每变更项 varint(key)+zigzag varint(value)，前置 count varint。
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(64 + dirty * 12);
        VarInts.writeVarInt(b, d.entries().size());
        int logical = 0;
        for (var e : d.entries().entrySet()) {
            VarInts.writeVarInt(b, e.getKey());
            VarInts.writeVarInt(b, VarInts.zigZagEncode((int) e.getValue().value()));
            logical += varIntSize(e.getKey()) + varIntSize(VarInts.zigZagEncode((int) e.getValue().value()));
        }
        return java.util.Arrays.copyOf(b.array(), b.position());
    }

    public static void main(String[] args) {
        int updates = args.length > 0 ? Integer.parseInt(args[0]) : 10000;
        int fields = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        int dirtyRatio = args.length > 2 ? Integer.parseInt(args[2]) : 25;
        Random rnd = new Random(42);

        FrameCodec codec = new FrameCodec();
        Snapshot snap = new Snapshot();
        Metrics m = new Metrics();

        for (int u = 0; u < updates; u++) {
            // ---- 优化路径：一帧承载本轮所有变更 ----
            byte[] delta = deltaPayload(snap, u, fields, dirtyRatio, rnd);
            Frame f = Frame.data(u, u, u - 1, 1, Flags.RELIABLE_ORDERED | Flags.PRI_STATE,
                    List.of(new Message(FrameType.DELTA, delta)));
            long t0 = System.nanoTime();
            byte[] wire = codec.encode(f);
            m.optimizedNanos += System.nanoTime() - t0;
            m.optimizedBytes += wire.length;
            m.optimizedPackets += 1;
            m.logicalBytes += varIntSize(delta.length) + delta.length; // 逻辑载荷（帧内有效字节）

            // ---- 基线路径：每个字段一帧 JSON 文本 ----
            int dirty = Math.max(1, fields * dirtyRatio / 100);
            for (int k = 0; k < dirty; k++) {
                int key = rnd.nextInt(fields);
                byte[] json = ("{\"field_" + key + "\":" + rnd.nextInt(1_000_000) + "}")
                        .getBytes(StandardCharsets.UTF_8);
                Frame bf = Frame.data(u, u, 0, 0, Flags.RELIABLE_ORDERED | Flags.PRI_STATE,
                        List.of(new Message(FrameType.RAW, json)));
                long t1 = System.nanoTime();
                byte[] bwire = codec.encode(bf);
                m.baselineNanos += System.nanoTime() - t1;
                m.baselineBytes += bwire.length;
                m.baselinePackets += 1;
            }
        }

        // 输出 TSV（供 bench.py 汇总与 CSV 落盘）
        System.out.printf("%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d%n",
                updates, fields, dirtyRatio,
                m.baselineBytes, m.optimizedBytes, m.logicalBytes,
                m.baselinePackets, m.optimizedPackets,
                m.baselineNanos, m.optimizedNanos);
    }
}
