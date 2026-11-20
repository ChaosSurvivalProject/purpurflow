package com.purpurflow.server.channel;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;

/** 通道注册（L0/H1）：注册 purpurflow:main 收发通道。通道常量双端共享同一标识。 */
public final class ChannelRegistrar {
    public static final String CHANNEL = "purpurflow:main";

    private final Plugin plugin;
    private final Messenger messenger;

    public ChannelRegistrar(Plugin plugin) {
        this.plugin = plugin;
        this.messenger = plugin.getServer().getMessenger();
    }

    /** enabled=false 时完全不注册出站，实现一键回滚且不影响 vanilla（H2）。 */
    public void register(boolean enabled, InboundDispatcher dispatcher) {
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, dispatcher);
        if (enabled) {
            messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
    }

    public void unregister() {
        messenger.unregisterIncomingPluginChannel(plugin, CHANNEL);
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }
}
