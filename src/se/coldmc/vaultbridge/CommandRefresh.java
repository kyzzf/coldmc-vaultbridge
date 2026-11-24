package se.coldmc.vaultbridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Resends a player's command list once their rank has loaded (Flake, 2026-10-30).
 * The server sends the list at join, and CoreTools attaches the core-perms nodes a moment later
 * (PlayerDataLoadedEvent) without sending it again. So a Mod's client did not know /tp existed:
 * no suggestions, red text, and a bare /tp looked dead (the owner, 2026-10-30: "/tp doesnt work").
 * The server still ran it, because permission is checked again on execution. Changing world
 * resent the list, which is why it sometimes worked. Sent twice after join, and once after a
 * profile is picked (ProfileGate), because CoreTools loads data asynchronously.
 */
final class CommandRefresh implements Listener {
    private static Plugin plugin;

    CommandRefresh(Plugin p) { plugin = p; }

    static void later(Player p, long ticks) {
        if (plugin == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) p.updateCommands(); }, ticks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        later(e.getPlayer(), 40);
        later(e.getPlayer(), 200);
    }
}
