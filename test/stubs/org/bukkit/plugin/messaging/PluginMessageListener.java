package org.bukkit.plugin.messaging;

import org.bukkit.entity.Player;

/** 编译用最小桩。 */
public interface PluginMessageListener {
    void onPluginMessageReceived(String channel, Player player, byte[] message);
}
