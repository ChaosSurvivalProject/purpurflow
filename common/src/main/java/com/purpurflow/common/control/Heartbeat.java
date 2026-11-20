package com.purpurflow.common.control;

/** HEARTBEAT：携带本地时间戳用于 RTT 估算，配合捎带 ack 做拥塞控制（H3）。 */
public final class Heartbeat {
    private final long timestamp;

    public Heartbeat(long timestamp) { this.timestamp = timestamp; }

    public long timestamp() { return timestamp; }
}
