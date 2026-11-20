package net.minecraft.network.codec;

/** 编译用最小桩。 */
public interface StreamCodec<B, V> {
    void encode(B buf, V value);
    V decode(B buf);
    static <B, V> StreamCodec<B, V> of(java.util.function.BiConsumer<B, V> enc,
                                       java.util.function.Function<B, V> dec) {
        return new StreamCodec<>() {
            public void encode(B b, V v) { enc.accept(b, v); }
            public V decode(B b) { return dec.apply(b); }
        };
    }
}
