package se.coldmc.vaultbridge;

import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/** Read-only: ranks are managed in plugins/CoreTools/core-perms.yml, so the setters do nothing. */
final class ChatBridge extends Chat {
    ChatBridge(Permission perms) { super(perms); }

    private static OfflinePlayer player(String name) {
        OfflinePlayer p = Bukkit.getPlayerExact(name);
        return p != null ? p : Bukkit.getOfflinePlayer(name);
    }

    private static String ph(OfflinePlayer p, String placeholder) {
        if (p == null) return "";
        String v = PlaceholderAPI.setPlaceholders(p, placeholder);
        return v == null || v.equals(placeholder) ? "" : v;
    }

    @Override public String getName() { return "ColdmcVaultBridge"; }
    @Override public boolean isEnabled() { return true; }

    @Override public String getPlayerPrefix(String world, OfflinePlayer p) { return ph(p, "%coretools_perms_prefix%"); }
    @Override public String getPlayerSuffix(String world, OfflinePlayer p) { return ph(p, "%coretools_perms_suffix%"); }
    @Override public String getPlayerPrefix(String world, String name) { return getPlayerPrefix(world, player(name)); }
    @Override public String getPlayerSuffix(String world, String name) { return getPlayerSuffix(world, player(name)); }
    @Override public void setPlayerPrefix(String world, String name, String prefix) { }
    @Override public void setPlayerSuffix(String world, String name, String suffix) { }

    @Override public String getGroupPrefix(String world, String group) { return ""; }
    @Override public String getGroupSuffix(String world, String group) { return ""; }
    @Override public void setGroupPrefix(String world, String group, String prefix) { }
    @Override public void setGroupSuffix(String world, String group, String suffix) { }

    @Override public int getPlayerInfoInteger(String w, String p, String n, int d) { return d; }
    @Override public void setPlayerInfoInteger(String w, String p, String n, int v) { }
    @Override public int getGroupInfoInteger(String w, String g, String n, int d) { return d; }
    @Override public void setGroupInfoInteger(String w, String g, String n, int v) { }
    @Override public double getPlayerInfoDouble(String w, String p, String n, double d) { return d; }
    @Override public void setPlayerInfoDouble(String w, String p, String n, double v) { }
    @Override public double getGroupInfoDouble(String w, String g, String n, double d) { return d; }
    @Override public void setGroupInfoDouble(String w, String g, String n, double v) { }
    @Override public boolean getPlayerInfoBoolean(String w, String p, String n, boolean d) { return d; }
    @Override public void setPlayerInfoBoolean(String w, String p, String n, boolean v) { }
    @Override public boolean getGroupInfoBoolean(String w, String g, String n, boolean d) { return d; }
    @Override public void setGroupInfoBoolean(String w, String g, String n, boolean v) { }
    @Override public String getPlayerInfoString(String w, String p, String n, String d) { return d; }
    @Override public void setPlayerInfoString(String w, String p, String n, String v) { }
    @Override public String getGroupInfoString(String w, String g, String n, String d) { return d; }
    @Override public void setGroupInfoString(String w, String g, String n, String v) { }
}
