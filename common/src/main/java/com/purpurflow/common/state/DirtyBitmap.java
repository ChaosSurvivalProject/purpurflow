package com.purpurflow.common.state;

import com.purpurflow.common.protocol.BitPacker;

/**
 * 脏位图：标记本轮变更的字段（按字段位），替代整对象重发。
 * 超过 64 字段时用 long[] 扩展；默认实现覆盖 64 字段。
 */
public final class DirtyBitmap {
    private long bits;

    public DirtyBitmap() { this(0L); }
    public DirtyBitmap(long bits) { this.bits = bits; }

    public void mark(int fieldIndex) { bits = BitPacker.set(bits, fieldIndex, true); }
    public boolean isDirty(int fieldIndex) { return BitPacker.get(bits, fieldIndex); }
    public long bits() { return bits; }
    public int count() { return Long.bitCount(bits); }
    public boolean isEmpty() { return bits == 0; }

    /** 合并另一张脏位图（背压丢弃时把可丢弃帧合并进下一帧）。 */
    public void merge(DirtyBitmap other) { bits |= other.bits; }

    public void clear() { bits = 0; }
}
