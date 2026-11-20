package net.minecraft.network;

/** 编译用最小桩：模拟 Netty ByteBuf 的裸字节写 + FriendlyByteBuf 的带长度前缀读。 */
public class RegistryFriendlyByteBuf {
    private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

    /** Netty ByteBuf.writeBytes(byte[])：裸写，无长度前缀。 */
    public RegistryFriendlyByteBuf writeBytes(byte[] src) { out.write(src, 0, src.length); return this; }

    /** FriendlyByteBuf.readByteArray()：读 VarInt 长度前缀 + 该长度字节。此处仅占位返回空。 */
    public byte[] readByteArray() { return new byte[0]; }

    public byte[] snapshot() { return out.toByteArray(); }
}
