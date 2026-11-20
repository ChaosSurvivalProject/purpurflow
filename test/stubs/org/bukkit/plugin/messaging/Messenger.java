package org.bukkit.plugin.messaging;

import org.bukkit.plugin.Plugin;

/** 编译用最小桩。 */
public interface Messenger {
    void registerIncomingPluginChannel(Plugin plugin, String channel, PluginMessageListener listener);
    void registerOutgoingPluginChannel(Plugin plugin, String channel);
    void unregisterIncomingPluginChannel(Plugin plugin, String channel);
    void unregisterOutgoingPluginChannel(Plugin plugin, String channel);
}
