package com.purpurflow.common.control;

/** HELLO_ACK（S→C）：选定版本、会话 ID、字典版本、服务端分片上限、是否启用增强流。 */
public final class HelloAck {
    private final int selectedVersion;
    private final long sessionId;
    private final int dictVersion;
    private final int serverMaxChunk;
    private final boolean enhanced;

    public HelloAck(int selectedVersion, long sessionId, int dictVersion, int serverMaxChunk, boolean enhanced) {
        this.selectedVersion = selectedVersion;
        this.sessionId = sessionId;
        this.dictVersion = dictVersion;
        this.serverMaxChunk = serverMaxChunk;
        this.enhanced = enhanced;
    }

    public int selectedVersion() { return selectedVersion; }
    public long sessionId() { return sessionId; }
    public int dictVersion() { return dictVersion; }
    public int serverMaxChunk() { return serverMaxChunk; }
    public boolean enhanced() { return enhanced; }
}
