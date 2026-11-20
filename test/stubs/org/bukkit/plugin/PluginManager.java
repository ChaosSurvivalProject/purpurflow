package org.bukkit.plugin;

import org.bukkit.event.Listener;

/** 编译用最小桩。 */
public interface PluginManager {
    void registerEvents(Listener listener, Plugin plugin);
}
