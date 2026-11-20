package com.purpurflow.test;

import com.purpurflow.common.protocol.Frame;
import com.purpurflow.common.protocol.FrameType;
import com.purpurflow.common.protocol.Message;
import com.purpurflow.server.egress.BackpressureGate;
import com.purpurflow.server.egress.EgressPipeline;
import com.purpurflow.server.egress.Priority;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** R-07 H3 出站编排回归：优先级出队、背压丢弃、队列上限、限速排队、写回捕获 player。 */
class EgressPipelineRegressionTest {

    private static Frame frame(String tag) {
        return Frame.data(1L, 1L, 0L, 1, 0,
                List.of(new Message(FrameType.RAW, tag.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
    private static String tag(Frame f) {
        return new String(f.messages().get(0).payload(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 控制帧优先于遥测帧出队，即使遥测先入队；sink 捕获的 player 标签正确。 */
    @Test
    void BUG_G_controlFrameDrainsFirst() {
        EgressPipeline ep = new EgressPipeline(16, 10_000_000L, 1000, 16, p -> true);
        List<String> receivedBy = new ArrayList<>();
        ep.offer(frame("telemetry"), Priority.TELEMETRY, 10, "playerA", f -> { receivedBy.add("A:" + tag(f)); return true; });
        ep.offer(frame("control"), Priority.CONTROL, 10, "playerB", f -> { receivedBy.add("B:" + tag(f)); return true; });

        int sent = ep.drain(2, f -> 10);
        assertEquals(2, sent);
        assertEquals("B:control", receivedBy.get(0), "CONTROL 必须先出队且用 playerB 的 sink");
        assertEquals("A:telemetry", receivedBy.get(1));
    }

    /** 队列达上限后，低优先级新帧被丢弃并计数。 */
    @Test
    void BUG_G_queueBoundedAndLowPriorityDropped() {
        EgressPipeline ep = new EgressPipeline(2, 10_000_000L, 1000, 100, p -> true);
        EgressPipeline.Sink ok = f -> true;
        assertTrue(ep.offer(frame("t1"), Priority.TELEMETRY, 10, "p", ok));
        assertTrue(ep.offer(frame("t2"), Priority.TELEMETRY, 10, "p", ok));
        assertFalse(ep.offer(frame("t3"), Priority.TELEMETRY, 10, "p", ok), "队满且同优先级须丢弃");
        assertEquals(1, ep.droppedByFullQueue());
        assertEquals(2, ep.queueSize());

        assertTrue(ep.offer(frame("c"), Priority.CONTROL, 10, "p", ok), "高优先级帧可替换队尾");
        assertEquals(2, ep.queueSize());
    }

    /** 不可写时入站帧被背压丢弃并计数。 */
    @Test
    void BUG_G_backpressureDropsWhenNotWritable() {
        EgressPipeline ep = new EgressPipeline(16, 10_000_000L, 1000, 8, p -> false);
        assertFalse(ep.offer(frame("x"), Priority.STATE, 10, "p", f -> true));
        assertEquals(1, ep.droppedByBackpressure());
        assertEquals(0, ep.queueSize());
    }

    /** 限速：每秒仅 1 包，第 2 帧在 drain 时被延迟放回，不被丢弃。 */
    @Test
    void BUG_G_rateLimitDelaysNotDrops() {
        EgressPipeline ep = new EgressPipeline(16, 10_000_000L, 1, 16, p -> true);
        EgressPipeline.Sink ok = f -> true;
        ep.offer(frame("a"), Priority.STATE, 10, "p", ok);
        ep.offer(frame("b"), Priority.STATE, 10, "p", ok);
        int n = ep.drain(8, f -> 10);
        assertEquals(1, n, "本轮只应写出 1 帧（限速 1 pkt/s）");
        assertEquals(1, ep.delayedByRate(), "另一帧应因限速被延迟");
        assertEquals(1, ep.queueSize(), "被延迟帧须留在队列中，不得丢失");
    }
}
