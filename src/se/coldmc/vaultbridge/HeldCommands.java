package se.coldmc.vaultbridge;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /cmchold <player> <command...> (console only). Runs the console command now when the player is
 * inside an MMOProfiles profile; otherwise saves it in held.yml and runs it once they pick one.
 * Anything given on the profile select screen is wiped, and CoreTools cannot write account
 * variables there either, so login-time rewards (streaks, playtime, first join, offline votes)
 * are routed through this. Coins are held by EconomyBridge's ledger instead.
 *
 * held.yml also has a "profiles.<profile uuid>" section, written by the old-server data
 * conversion (migrate_old_server_data.py), never by a command. Those commands run only when
 * that exact profile is picked, once its MMOCore data has loaded, with {player} replaced by
 * the player's current name. That is how old skill levels reach each profile.
 */
final class HeldCommands implements CommandExecutor {
    private final JavaPlugin plugin;
    private final EconomyBridge eco;
    private final File file;
    private final YamlConfiguration held;

    HeldCommands(JavaPlugin plugin, EconomyBridge eco) {
        this.plugin = plugin;
        this.eco = eco;
        this.file = new File(plugin.getDataFolder(), "held.yml");
        this.held = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) return true;
        if (args.length < 2) { sender.sendMessage("Usage: /cmchold <player> <command...>"); return true; }
        String command = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Player p = Bukkit.getPlayerExact(args[0]);
        if (p != null && eco.inProfile(p)) { dispatch(command); return true; }
        OfflinePlayer o = p != null ? p : Bukkit.getOfflinePlayerIfCached(args[0]);
        if (o == null) { plugin.getLogger().warning("cmchold: unknown player " + args[0] + ", ran now: " + command); dispatch(command); return true; }
        synchronized (this) {
            String k = "held." + o.getUniqueId();
            List<String> list = new ArrayList<>(held.getStringList(k + ".commands"));
            list.add(command);
            held.set(k + ".name", o.getName());
            held.set(k + ".commands", list);
            save();
        }
        plugin.getLogger().info("Held for " + o.getName() + " until a profile is chosen: " + command);
        return true;
    }

    /** Called on profile select, after EconomyBridge has marked the player as in a profile. */
    void release(UUID id) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !eco.inProfile(p)) return;
            List<String> list;
            synchronized (this) {
                String k = "held." + id;
                list = held.getStringList(k + ".commands");
                if (list.isEmpty()) return;
                held.set(k, null);
                save();
            }
            for (String c : list) dispatch(c);
            plugin.getLogger().info("Released " + list.size() + " held reward command(s) to " + p.getName());
        }, 20L);
    }

    /** Called on profile select with the picked profile's id. */
    void releaseProfile(UUID account, UUID profile) {
        if (profile == null || !held.isList("profiles." + profile + ".commands")) return;
        new org.bukkit.scheduler.BukkitRunnable() {
            int tries = 0;
            @Override public void run() {
                Player p = Bukkit.getPlayer(account);
                if (p == null || !eco.inProfile(p) || ++tries > 60) { cancel(); return; }
                if (!mmocoreReady(p)) return;   // retry every second until MMOCore has the profile
                cancel();
                List<String> list;
                synchronized (HeldCommands.this) {
                    String k = "profiles." + profile;
                    list = held.getStringList(k + ".commands");
                    if (list.isEmpty()) return;
                    held.set(k, null);
                    save();
                }
                for (String c : list) dispatch(c.replace("{player}", p.getName()));
                plugin.getLogger().info("Released " + list.size() + " held profile command(s) to " + p.getName() + " (profile " + profile + ")");
            }
        }.runTaskTimer(plugin, 60L, 20L);
    }

    private static boolean mmocoreReady(Player p) {
        try {
            return net.Indyuce.mmocore.api.player.PlayerData.has(p)
                && net.Indyuce.mmocore.api.player.PlayerData.get(p).isFullyLoaded();
        } catch (NoClassDefFoundError e) { return true; }
    }

    private void dispatch(String c) { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c); }

    private void save() {
        try { held.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save held.yml: " + e); }
    }
}
