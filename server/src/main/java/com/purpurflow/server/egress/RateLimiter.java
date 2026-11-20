package com.purpurflow.server.egress;

/** 每玩家速率限制（H3）：每秒字节数与包数双配额，基于简单令牌桶。 */
public final class RateLimiter {
    private final long maxBytesPerSec;
    private final int maxPacketsPerSec;
    private long windowStartMs;
    private long bytesUsed;
    private int packetsUsed;

    public RateLimiter(long maxBytesPerSec, int maxPacketsPerSec) {
        this.maxBytesPerSec = maxBytesPerSec;
        this.maxPacketsPerSec = maxPacketsPerSec;
        this.windowStartMs = System.currentTimeMillis();
    }

    /** 尝试消费一次发送配额；失败则由调用方丢弃/合并。 */
    public synchronized boolean tryAcquire(int bytes) {
        long now = System.currentTimeMillis();
        if (now - windowStartMs >= 1000) {
            windowStartMs = now;
            bytesUsed = 0;
            packetsUsed = 0;
        }
        if (bytesUsed + bytes > maxBytesPerSec || packetsUsed + 1 > maxPacketsPerSec) {
            return false;
        }
        bytesUsed += bytes;
        packetsUsed += 1;
        return true;
    }
}
