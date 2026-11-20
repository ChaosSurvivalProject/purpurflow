package org.bukkit;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitScheduler;

/** 编译用最小桩。 */
public interface Server {
    Messenger getMessenger();
    PluginManager getPluginManager();
    BukkitScheduler getScheduler();
}
