package org.bukkit.entity;

import java.util.UUID;

/** 编译用最小桩。 */
public interface Player {
    UUID getUniqueId();
    void sendPluginMessage(Object source, String channel, byte[] message);
}
