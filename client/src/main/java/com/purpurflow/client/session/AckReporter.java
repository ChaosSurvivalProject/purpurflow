package com.purpurflow.client.session;

/** ack 上报（H3/H4）：记录已处理的最大 seq，随数据帧捎带，不单独发包。 */
public final class AckReporter {
    private volatile long maxAckedSeq = -1;

    public void note(long seq) {
        if (seq > maxAckedSeq) maxAckedSeq = seq;
    }

    public long current() { return maxAckedSeq; }

    public void reset(long seq) { this.maxAckedSeq = seq; }
}
