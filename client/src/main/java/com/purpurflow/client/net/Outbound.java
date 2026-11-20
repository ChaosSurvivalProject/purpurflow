package com.purpurflow.client.net;

import com.purpurflow.common.control.ControlFrame;
import com.purpurflow.common.control.Hello;
import com.purpurflow.common.control.Capability;
import com.purpurflow.common.protocol.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/** 出站（client → server）：HELLO 握手、ack 捎带、能力探测后发送（H2/H5）。 */
public final class Outbound {
    private final FrameCodec codec = new FrameCodec();
    private final SeqTrackerLocal seq = new SeqTrackerLocal();

    private static final class SeqTrackerLocal {
        private long v;
        long next() { return v++; }
    }

    /** 发送 HELLO（上行极简，远小于 32 KiB）。 */
    public void hello(int dictVersion, int localMaxChunk) {
        Capability cap = new Capability(1, dictVersion,
                Capability.FEATURE_ZIGZAG | Capability.FEATURE_DICT | Capability.FEATURE_DELTA,
                localMaxChunk);
        byte[] payload = ControlFrame.encodeHello(new Hello(cap));
        Frame frame = Frame.data(0L, seq.next(), 0L, 0,
                Flags.RELIABLE_ORDERED | Flags.PRI_CONTROL,
                List.of(new Message(FrameType.HELLO, payload)));
        send(frame);
    }

    private void send(Frame frame) {
        if (!ClientPlayNetworking.canSend(FlowPayload.TYPE)) return; // L6 探测
        ClientPlayNetworking.send(new FlowPayload(codec.encode(frame)));
    }
}
