package se.coldmc.vaultbridge;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Vault permissions over CoreTools CorePerms. Vault's fallback (SuperPerms) has no groups and
 * throws on getPrimaryGroup, which made Towny's prefix lookup fail and TownyChat print
 * {permprefix} literally. Groups come from plugins/CoreTools/core-perms.yml (ordered by
 * priority); a player is in a group when they hold group.<name>. Read-only: ranks are changed
 * in CoreTools, so every add/remove answers false.
 */
final class PermBridge extends Permission {
    private final File coreperms;
    private long loadedAt = -1;
    private List<String> byPriority = new ArrayList<>();   // highest first

    PermBridge(Plugin plugin) {
        this.plugin = plugin;
        this.coreperms = new File(plugin.getDataFolder().getParentFile(), "CoreTools/core-perms.yml");
    }

    private synchronized List<String> groups() {
        if (coreperms.lastModified() != loadedAt) {
            loadedAt = coreperms.lastModified();
            List<String> g = new ArrayList<>();
            ConfigurationSection s = YamlConfiguration.loadConfiguration(coreperms).getConfigurationSection("core_perms.groups");
            if (s != null) {
                g.addAll(s.getKeys(false));
                g.sort((a, b) -> Integer.compare(s.getInt(b + ".priority"), s.getInt(a + ".priority")));
            }
            byPriority = g;
        }
        return byPriority;
    }

    private static Player online(String name) { return name == null ? null : Bukkit.getPlayerExact(name); }

    @Override public String getName() { return "ColdmcVaultBridge"; }
    @Override public boolean isEnabled() { return true; }
    @Override public boolean hasSuperPermsCompat() { return true; }
    @Override public boolean hasGroupSupport() { return true; }

    @Override public boolean playerHas(String world, String player, String perm) {
        Player p = online(player);
        return p != null && p.hasPermission(perm);
    }
    @Override public boolean playerAdd(String world, String player, String perm) { return false; }
    @Override public boolean playerRemove(String world, String player, String perm) { return false; }
    @Override public boolean groupHas(String world, String group, String perm) { return false; }
    @Override public boolean groupAdd(String world, String group, String perm) { return false; }
    @Override public boolean groupRemove(String world, String group, String perm) { return false; }

    @Override public boolean playerInGroup(String world, String player, String group) {
        Player p = online(player);
        if (p == null) return false;
        return "default".equalsIgnoreCase(group) || p.hasPermission("group." + group.toLowerCase());
    }
    @Override public boolean playerAddGroup(String world, String player, String group) { return false; }
    @Override public boolean playerRemoveGroup(String world, String player, String group) { return false; }

    @Override public String[] getPlayerGroups(String world, String player) {
        Player p = online(player);
        List<String> out = new ArrayList<>();
        for (String g : groups())
            if (p != null && p.hasPermission("group." + g)) out.add(g);
        if (out.isEmpty()) out.add("default");
        return out.toArray(new String[0]);
    }
    @Override public String getPrimaryGroup(String world, String player) { return getPlayerGroups(world, player)[0]; }
    @Override public String[] getGroups() { return groups().toArray(new String[0]); }
}
