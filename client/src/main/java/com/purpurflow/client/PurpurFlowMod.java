package com.purpurflow.client;

import com.purpurflow.client.compat.VanillaFallback;
import com.purpurflow.client.net.Outbound;
import com.purpurflow.client.net.PayloadRegistrations;
import com.purpurflow.client.session.AckReporter;
import com.purpurflow.client.session.LocalMirror;
import net.fabricmc.api.ClientModInitializer;

/** Fabric Mod 入口（client 端）。注册 payload、收发、维护本地镜像与降级状态。 */
public final class PurpurFlowMod implements ClientModInitializer {
    private final VanillaFallback fallback = new VanillaFallback();
    private final AckReporter ack = new AckReporter();
    private final LocalMirror mirror = new LocalMirror(256);
    private final Outbound outbound = new Outbound();

    @Override
    public void onInitializeClient() {
        PayloadRegistrations.register();
        // 入站注册在 play 阶段（configuration 阶段不做增强流，规避登录时序坑）
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.purpurflow.client.net.FlowPayload.TYPE,
                (payload, context) -> {
                    // handler 在主线程；只做轻量分发
                    new com.purpurflow.client.session.Inbound(mirror, ack, fallback).handle(payload);
                });
    }

    /** 连接建立后由 PlayConnectionEvents.JOIN 调用，发起握手。 */
    public void onJoin() {
        outbound.hello(1, 32768);
    }

    /** 断线时重置，重连走 RESUME（H4）。 */
    public void onDisconnect() {
        fallback.toVanilla("disconnected"); // 骨架：实际应进入 RESUME 流程再决定
    }
}
