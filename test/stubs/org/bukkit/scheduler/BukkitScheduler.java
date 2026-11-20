package org.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

/** 编译用最小桩。 */
public interface BukkitScheduler {
    Object runTaskTimer(Plugin plugin, Runnable task, long delay, long period);
}
