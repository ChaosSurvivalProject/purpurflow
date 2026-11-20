package com.purpurflow.client.net;

import com.purpurflow.common.protocol.Frame;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Fabric 自定义 payload（L0/L1）：承载一帧字节。
 * 1.20.5+ 新网络 API：CustomPacketPayload + StreamCodec + CustomPacketPayload.Type。
 * 1.21.11 采用 Mojang mappings（Identifier.fromNamespaceAndPath）。
 */
public record FlowPayload(byte[] data) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("purpurflow", "main");
    public static final CustomPacketPayload.Type<FlowPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    /** 从 byte[] 读写（简单定长字节流，长度由剩余字节推断）。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, FlowPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBytes(payload.data),
                    buf -> new FlowPayload(buf.readByteArray())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
