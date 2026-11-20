package com.purpurflow.server.egress;

import com.purpurflow.common.protocol.Message;

import java.util.ArrayList;
import java.util.List;

/** 合批器（L1）：把多条小消息合并进一帧，降低每逻辑信息的包开销。 */
public final class Batcher {
    private final int maxMessagesPerFrame;

    public Batcher(int maxMessagesPerFrame) {
        this.maxMessagesPerFrame = maxMessagesPerFrame;
    }

    /** 将消息列表按上限切分为若干批，每批最终编码为一帧。 */
    public List<List<Message>> batch(List<Message> messages) {
        List<List<Message>> out = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += maxMessagesPerFrame) {
            out.add(new ArrayList<>(messages.subList(i, Math.min(i + maxMessagesPerFrame, messages.size()))));
        }
        return out;
    }
}
