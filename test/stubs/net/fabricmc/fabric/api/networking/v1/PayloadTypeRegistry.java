package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 编译用最小桩。 */
public final class PayloadTypeRegistry {
    public static final class PlayRegistrar {
        public <T extends CustomPacketPayload> void registerLarge(
                CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec, int max) {}
        public <T extends CustomPacketPayload> void register(
                CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {}
    }
    public static PlayRegistrar playS2C() { return new PlayRegistrar(); }
    public static PlayRegistrar playC2S() { return new PlayRegistrar(); }
}
