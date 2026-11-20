package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 编译用最小桩。 */
public final class ClientPlayNetworking {
    public static boolean canSend(CustomPacketPayload.Type<?> type) { return true; }
    public static void send(CustomPacketPayload payload) {}
    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
            CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {}

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }
    public interface Context {}
}
