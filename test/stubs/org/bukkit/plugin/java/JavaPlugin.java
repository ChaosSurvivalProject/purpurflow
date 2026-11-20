package org.bukkit.plugin.java;

import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/** 编译用最小桩。 */
public abstract class JavaPlugin implements Plugin {
    @Override public Server getServer() { return null; }
    public void onEnable() {}
    public void onDisable() {}
    public void saveDefaultConfig() {}
    public FileConfiguration getConfig() { return null; }
    public java.util.logging.Logger getLogger() { return java.util.logging.Logger.getLogger("PurpurFlow"); }
}
