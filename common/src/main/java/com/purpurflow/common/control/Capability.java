package com.purpurflow.common.control;

/**
 * 能力位图（H5）：协议版本、字典版本、支持的编码/压缩、分片上限。
 * 位定义便于双方取交集，协商出共同可用版本。
 */
public final class Capability {
    private final int protocolVersion;
    private final int dictVersion;
    private final int featureBits;
    private final int localMaxChunk;

    // feature bits
    public static final int FEATURE_ZIGZAG = 1 << 0;
    public static final int FEATURE_DICT = 1 << 1;
    public static final int FEATURE_DELTA = 1 << 2;
    public static final int FEATURE_COMPRESSION = 1 << 3;
    public static final int FEATURE_LARGE_PAYLOAD = 1 << 4;

    public Capability(int protocolVersion, int dictVersion, int featureBits, int localMaxChunk) {
        this.protocolVersion = protocolVersion;
        this.dictVersion = dictVersion;
        this.featureBits = featureBits;
        this.localMaxChunk = localMaxChunk;
    }

    public int protocolVersion() { return protocolVersion; }
    public int dictVersion() { return dictVersion; }
    public int featureBits() { return featureBits; }
    public int localMaxChunk() { return localMaxChunk; }

    public boolean supports(int feature) { return (featureBits & feature) != 0; }

    /** 与对端取能力交集。 */
    public int intersectFeatures(Capability other) { return this.featureBits & other.featureBits; }
}
