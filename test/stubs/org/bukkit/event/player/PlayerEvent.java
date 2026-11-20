package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/** 编译用最小桩。 */
public abstract class PlayerEvent extends Event {
    private final Player player;
    protected PlayerEvent(Player p) { this.player = p; }
    public Player getPlayer() { return player; }
}
