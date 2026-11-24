package se.coldmc.vaultbridge;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ColdMC era of each MMOProfiles profile (Flake, 2026-10-23). the owner asked for the Nexo
 * resource pack to be split per mode/era and for loot to never come from a higher era than the
 * player is in, and nothing on the server tracked an era until now.
 *
 *   0 Pre-Dragon   1 Post-Dragon   2 Post-Empress   3 Post-Hydra
 *
 * An era only ever goes up. It rises when the Ender Dragon dies within dragon-radius of the
 * player, or when a catalyst key from config.yml (eras.catalysts) is equipped - every 5 s the
 * tracker reads %javascript_catalyst_<ID>% for the keys above the current era. It is stored per
 * PROFILE in eras.yml, because a new character must start Pre-Dragon; without MMOProfiles the
 * player's own UUID is the key.
 *
 * Read in game as %coldmc_era% (0-3) and %coldmc_era_name%; the loot pouches, crates and NPC
 * shops are gated on it (knowledge/runtime/scripts/coldmc_eras.py). PackSync turns it into the
 * player's Nexo multipack selection. /coldmcera <player> [set <0-3>] reads or overrides it.
 */
final class EraTracker implements Listener, CommandExecutor, TabCompleter {
    static final String[] NAMES = {"Pre-Dragon", "Post-Dragon", "Post-Empress", "Post-Hydra"};
    static final int MAX = NAMES.length - 1;
    private static final String GAIA = "§8[§dɢᴀɪᴀ§8] ";

    interface PackSync { void sync(Player p, int era, boolean resend); }

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final boolean profiles;
    private final Map<UUID, String> keyOf = new ConcurrentHashMap<>();   // player -> profile key
    private final Map<String, Integer> catalysts = new LinkedHashMap<>();
    private final double dragonRadius;
    private PackSync packs;

    EraTracker(JavaPlugin plugin, boolean profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.file = new File(plugin.getDataFolder(), "eras.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("eras.catalysts");
        if (c != null) for (String id : c.getKeys(false)) catalysts.put(id, Math.min(MAX, c.getInt(id)));
        this.dragonRadius = plugin.getConfig().getDouble("eras.dragon-radius", 200);
    }

    void setPackSync(PackSync s) { packs = s; }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkCatalysts, 100L, 100L);
        if (!profiles) for (Player p : Bukkit.getOnlinePlayers()) keyOf.put(p.getUniqueId(), p.getUniqueId().toString());
    }

    /** The player's era, or 0 while they are on the profile select screen. */
    int era(Player p) {
        String k = p == null ? null : keyOf.get(p.getUniqueId());
        return k == null ? 0 : data.getInt("eras." + k, 0);
    }

    boolean inEra(Player p) { return p != null && keyOf.containsKey(p.getUniqueId()); }

    // ------------------------------------------------------------ profile lifecycle

    void profileEntered(Player p, UUID profile) {
        keyOf.put(p.getUniqueId(), profile.toString());
        data.set("names." + profile, p.getName());
        if (packs != null) packs.sync(p, era(p), true);
    }

    void profileLeft(Player p) {
        // the selection stays on the player, so their next login is sent this profile's packs
        // straight away; it is corrected on select if they pick a different character
        keyOf.remove(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!profiles) {
            keyOf.put(e.getPlayer().getUniqueId(), e.getPlayer().getUniqueId().toString());
            if (packs != null) packs.sync(e.getPlayer(), era(e.getPlayer()), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { keyOf.remove(e.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------ what raises an era

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragon(EntityDeathEvent e) {
        if (e.getEntityType() != EntityType.ENDER_DRAGON) return;
        double r2 = dragonRadius * dragonRadius;
        for (Player p : e.getEntity().getWorld().getPlayers())
            if (p.getLocation().distanceSquared(e.getEntity().getLocation()) <= r2)
                raise(p, 1, "the Ender Dragon fell");
    }

    private void checkCatalysts() {
        if (catalysts.isEmpty() || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!inEra(p)) continue;
            int now = era(p), best = now;
            for (Map.Entry<String, Integer> c : catalysts.entrySet()) {
                if (c.getValue() <= best) continue;
                String v = PlaceholderAPI.setPlaceholders(p, "%javascript_catalyst_" + c.getKey() + "%");
                if ("1".equals(v.trim())) best = c.getValue();
            }
            if (best > now) raise(p, best, "a key was equipped");
        }
    }

    void raise(Player p, int to, String why) {
        if (!inEra(p) || to <= era(p)) return;
        set(p, to);
        plugin.getLogger().info("Era: " + p.getName() + " -> " + to + " " + NAMES[to] + " (" + why + ")");
        p.sendMessage(GAIA + "§7A new age opens before you: §f" + NAMES[to] + "§7.");
    }

    private void set(Player p, int to) {
        String k = keyOf.get(p.getUniqueId());
        if (k == null) return;
        data.set("eras." + k, to);
        data.set("names." + k, p.getName());
        save();
        if (packs != null) packs.sync(p, to, true);
    }

    private void save() {
        try { data.save(file); } catch (IOException ex) { plugin.getLogger().warning("eras.yml: " + ex.getMessage()); }
    }

    // ------------------------------------------------------------ /coldmcera

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("coldmc.era.admin")) return true;
        if (args.length < 1) { sender.sendMessage("Usage: /coldmcera <player> [set <0-" + MAX + ">]"); return true; }
        Player p = Bukkit.getPlayerExact(args[0]);
        if (p == null) { sender.sendMessage("Not online: " + args[0]); return true; }
        if (!inEra(p)) { sender.sendMessage(p.getName() + " is not inside a profile yet."); return true; }
        if (args.length >= 3 && args[1].equalsIgnoreCase("set")) {
            int to;
            try { to = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { to = -1; }
            if (to < 0 || to > MAX) { sender.sendMessage("Era must be 0-" + MAX); return true; }
            set(p, to);   // an admin override may also lower it
            plugin.getLogger().info("Era: " + p.getName() + " set to " + to + " by " + sender.getName());
        }
        int e = era(p);
        sender.sendMessage(p.getName() + ": era " + e + " (" + NAMES[e] + ")");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("coldmc.era.admin")) return out;
        if (args.length == 1) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        else if (args.length == 2) out.add("set");
        else if (args.length == 3) for (int i = 0; i <= MAX; i++) out.add(String.valueOf(i));
        return out;
    }
}
