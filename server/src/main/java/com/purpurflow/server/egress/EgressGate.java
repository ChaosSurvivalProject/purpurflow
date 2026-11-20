package com.purpurflow.server.egress;

import com.purpurflow.common.protocol.Frame;
import org.bukkit.entity.Player;

/**
 * 出站闸门（H3 关键接口契约）：
 * offer(player, frame, priority) -> boolean，false = 背压丢弃，调用方必须处理（合并脏位图/计数）。
 */
public interface EgressGate {
    boolean offer(Player player, Frame frame, Priority priority);
}
