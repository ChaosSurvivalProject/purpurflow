package net.minecraft.network.protocol.common.custom;

import net.minecraft.resources.Identifier;

/** 编译用最小桩。 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    final class Type<T extends CustomPacketPayload> {
        private final Identifier id;
        public Type(Identifier id) { this.id = id; }
        public Identifier id() { return id; }
    }
}
