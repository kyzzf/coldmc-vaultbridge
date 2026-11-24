package se.coldmc.vaultbridge;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Online players go to CoreTools' economy, the only accounts it can serve.
 * Offline players (anyone who has played before) are booked in a ledger: their balance is the
 * CoreTools balance recorded when they logged out plus what was booked since, and the net is
 * paid into or taken from their real CoreTools coins once they are back and their profile
 * is loaded.
 * A player who is online but has not chosen an MMOProfiles profile yet (the select screen) is
 * treated as offline: CoreTools has no profile to put coins in, and whatever it is given there is
 * lost. So login-time rewards (streaks, playtime, offline votes) go to the ledger too, and the
 * ledger settles on profile select, never on join.
 * Every other account (Towny "town-"/"nation-" banks, Towny's server account, any NPC UUID) is
 * a bank kept here. All of it lives in plugins/ColdmcVaultBridge/banks.yml.
 */
final class EconomyBridge implements Economy, Listener {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration banks;
    /** Players inside a profile. Null without MMOProfiles: then every online player counts. */
    private Set<UUID> inProfile;

    EconomyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "banks.yml");
        this.banks = YamlConfiguration.loadConfiguration(file);
    }

    /** CoreTools' provider: the highest Economy registration that is not this one. */
    private Economy core() {
        for (RegisteredServiceProvider<Economy> r : Bukkit.getServicesManager().getRegistrations(Economy.class))
            if (r.getProvider() != this) return r.getProvider();
        return null;
    }

    /** Called only when MMOProfiles is present. The bridge loads at boot, so nobody is online yet. */
    void gateProfiles() { inProfile = ConcurrentHashMap.newKeySet(); }

    private HeldCommands heldCommands;
    void setHeld(HeldCommands h) { heldCommands = h; }
    boolean inProfile(Player p) { return ready(p); }

    private boolean ready(Player p) { return p != null && (inProfile == null || inProfile.contains(p.getUniqueId())); }

    /** Online AND inside a profile - the only players CoreTools can really pay. */
    private Player online(OfflinePlayer p) {
        Player o = p == null ? null : Bukkit.getPlayer(p.getUniqueId());
        return ready(o) ? o : null;
    }

    private static String key(OfflinePlayer p) { return "uuid." + p.getUniqueId(); }
    private static String key(String name) { return "name." + name.toLowerCase().replace('.', '_'); }

    private synchronized double bal(String k) { return banks.getDouble(k + ".balance", 0); }

    private synchronized EconomyResponse add(String k, String label, double amount) {
        if (amount < 0) return new EconomyResponse(0, bal(k), ResponseType.FAILURE, "Negative amount");
        double nb = bal(k) + amount;
        banks.set(k + ".name", label);
        banks.set(k + ".balance", nb);
        save();
        return new EconomyResponse(amount, nb, ResponseType.SUCCESS, null);
    }

    private synchronized EconomyResponse take(String k, String label, double amount) {
        if (amount < 0) return new EconomyResponse(0, bal(k), ResponseType.FAILURE, "Negative amount");
        double b = bal(k);
        if (b < amount) return new EconomyResponse(0, b, ResponseType.FAILURE, "Insufficient funds");
        banks.set(k + ".name", label);
        banks.set(k + ".balance", b - amount);
        save();
        return new EconomyResponse(amount, b - amount, ResponseType.SUCCESS, null);
    }

    private void save() {
        try { banks.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save banks.yml: " + e); }
    }

    private static EconomyResponse fail(String why) { return new EconomyResponse(0, 0, ResponseType.FAILURE, why); }

    // ---- the Economy interface ------------------------------------------------------------
    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "ColdmcVaultBridge"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { Economy c = core(); return c == null ? 2 : c.fractionalDigits(); }
    @Override public String format(double v) { Economy c = core(); return c == null ? String.valueOf(v) : c.format(v); }
    @Override public String currencyNamePlural() { Economy c = core(); return c == null ? "" : c.currencyNamePlural(); }
    @Override public String currencyNameSingular() { Economy c = core(); return c == null ? "" : c.currencyNameSingular(); }

    // A name is a player's if that player is online; anything else is a bank account here.
    private Player online(String name) {
        Player o = name == null ? null : Bukkit.getPlayerExact(name);
        return ready(o) ? o : null;
    }

    @Override public boolean hasAccount(OfflinePlayer p) { return true; }
    @Override public boolean hasAccount(OfflinePlayer p, String w) { return true; }
    @Override public boolean hasAccount(String n) { return true; }
    @Override public boolean hasAccount(String n, String w) { return true; }

    // ---- routing -----------------------------------------------------------------------------
    /** A real player CoreTools cannot pay yet: offline and played before, or online on the select screen. */
    private OfflinePlayer offlineReal(OfflinePlayer p) {
        if (p == null || online(p) != null) return null;
        return Bukkit.getPlayer(p.getUniqueId()) != null || p.hasPlayedBefore() ? p : null;
    }
    private OfflinePlayer offlineReal(String name) {
        if (name == null || online(name) != null) return null;
        Player selecting = Bukkit.getPlayerExact(name);
        if (selecting != null) return selecting;
        OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(name);
        return p != null && p.hasPlayedBefore() ? p : null;
    }

    private static String ledger(OfflinePlayer p) { return "ledger." + p.getUniqueId(); }
    private synchronized double offBal(OfflinePlayer p) {
        String k = ledger(p);
        return banks.getDouble(k + ".snapshot", 0) + banks.getDouble(k + ".pending", 0);
    }
    private synchronized EconomyResponse offAdd(OfflinePlayer p, double a) {
        if (a < 0) return fail("Negative amount");
        String k = ledger(p);
        banks.set(k + ".name", p.getName());
        banks.set(k + ".pending", banks.getDouble(k + ".pending", 0) + a);
        save();
        return new EconomyResponse(a, offBal(p), ResponseType.SUCCESS, null);
    }
    private synchronized EconomyResponse offTake(OfflinePlayer p, double a) {
        if (a < 0) return fail("Negative amount");
        double b = offBal(p);
        if (b < a) return new EconomyResponse(0, b, ResponseType.FAILURE, "Insufficient funds");
        String k = ledger(p);
        banks.set(k + ".name", p.getName());
        banks.set(k + ".pending", banks.getDouble(k + ".pending", 0) - a);
        save();
        return new EconomyResponse(a, b - a, ResponseType.SUCCESS, null);
    }

    @Override public double getBalance(OfflinePlayer p) {
        Player o = online(p); Economy c = core();
        if (o != null && c != null) return c.getBalance(o);
        OfflinePlayer off = offlineReal(p);
        return off != null ? offBal(off) : bal(key(p));
    }
    @Override public double getBalance(String n) {
        Player o = online(n); Economy c = core();
        if (o != null && c != null) return c.getBalance(o);
        OfflinePlayer off = offlineReal(n);
        return off != null ? offBal(off) : bal(key(n));
    }
    @Override public double getBalance(OfflinePlayer p, String w) { return getBalance(p); }
    @Override public double getBalance(String n, String w) { return getBalance(n); }

    @Override public boolean has(OfflinePlayer p, double a) { return getBalance(p) >= a; }
    @Override public boolean has(String n, double a) { return getBalance(n) >= a; }
    @Override public boolean has(OfflinePlayer p, String w, double a) { return has(p, a); }
    @Override public boolean has(String n, String w, double a) { return has(n, a); }

    @Override public EconomyResponse withdrawPlayer(OfflinePlayer p, double a) {
        Player o = online(p); Economy c = core();
        if (o != null && c != null) return c.withdrawPlayer(o, a);
        OfflinePlayer off = offlineReal(p);
        return off != null ? offTake(off, a) : take(key(p), String.valueOf(p.getName()), a);
    }
    @Override public EconomyResponse withdrawPlayer(String n, double a) {
        Player o = online(n); Economy c = core();
        if (o != null && c != null) return c.withdrawPlayer(o, a);
        OfflinePlayer off = offlineReal(n);
        return off != null ? offTake(off, a) : take(key(n), n, a);
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer p, String w, double a) { return withdrawPlayer(p, a); }
    @Override public EconomyResponse withdrawPlayer(String n, String w, double a) { return withdrawPlayer(n, a); }

    @Override public EconomyResponse depositPlayer(OfflinePlayer p, double a) {
        Player o = online(p); Economy c = core();
        if (o != null && c != null) return c.depositPlayer(o, a);
        OfflinePlayer off = offlineReal(p);
        return off != null ? offAdd(off, a) : add(key(p), String.valueOf(p.getName()), a);
    }
    @Override public EconomyResponse depositPlayer(String n, double a) {
        Player o = online(n); Economy c = core();
        if (o != null && c != null) return c.depositPlayer(o, a);
        OfflinePlayer off = offlineReal(n);
        return off != null ? offAdd(off, a) : add(key(n), n, a);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer p, String w, double a) { return depositPlayer(p, a); }
    @Override public EconomyResponse depositPlayer(String n, String w, double a) { return depositPlayer(n, a); }

    // ---- the ledger: snapshot on leaving a profile, settle once one is chosen ------------------
    /** Records the CoreTools balance of the profile being left. Only ever from inside one. */
    void profileLeft(Player pl) {
        if (pl == null || !ready(pl)) return;
        Economy c = core();
        if (c != null) {
            try {
                double b = c.getBalance(pl);
                synchronized (this) {
                    String k = "ledger." + pl.getUniqueId();
                    banks.set(k + ".name", pl.getName());
                    banks.set(k + ".snapshot", b);
                    save();
                }
            } catch (Throwable ignored) { }
        }
        if (inProfile != null) inProfile.remove(pl.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) { profileLeft(e.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        if (inProfile != null) inProfile.remove(e.getPlayer().getUniqueId());
        else settleSoon(e.getPlayer().getUniqueId());   // no MMOProfiles: join is as good as it gets
    }

    void profileEntered(Player pl, UUID profile) {
        if (pl == null) return;
        if (inProfile != null) inProfile.add(pl.getUniqueId());
        settleSoon(pl.getUniqueId());
        if (heldCommands != null) {
            heldCommands.release(pl.getUniqueId());
            heldCommands.releaseProfile(pl.getUniqueId(), profile);
        }
    }

    private void settleSoon(UUID id) {
        new BukkitRunnable() {
            int tries = 0;
            @Override public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !ready(p) || ++tries > 300 || settle(p)) cancel();
            }
        }.runTaskTimer(plugin, 20L, 40L);   // from 1 s, every 2 s for up to 10 min
    }

    /** True when there is nothing left to settle. */
    private boolean settle(Player p) {
        String k = "ledger." + p.getUniqueId();
        double pending;
        synchronized (this) { pending = banks.getDouble(k + ".pending", 0); }
        if (pending == 0) return true;
        Economy c = core();
        if (c == null) return false;
        try {
            double bal = c.getBalance(p);   // throws until CoreTools has the profile loaded
            EconomyResponse r;
            double applied;
            if (pending > 0) { r = c.depositPlayer(p, pending); applied = pending; }
            else { applied = -Math.min(-pending, bal); r = applied == 0 ? null : c.withdrawPlayer(p, -applied); }
            if (r != null && !r.transactionSuccess()) return false;
            synchronized (this) { banks.set(k + ".pending", 0); save(); }
            String amount = c.format(Math.abs(applied));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', applied >= 0
                    ? "&8[&dɢᴀɪᴀ&8] &7While you were away, &f" + amount + "&7 found its way to you."
                    : "&8[&dɢᴀɪᴀ&8] &7While you were away, &f" + amount + "&7 was paid from your purse."));
            plugin.getLogger().info("Settled " + applied + " into " + p.getName() + "'s profile");
            if (applied < 0 && -applied < -pending)
                plugin.getLogger().warning(p.getName() + " owed " + (-pending) + " but had " + bal + "; took " + (-applied));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public boolean createPlayerAccount(OfflinePlayer p) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer p, String w) { return true; }
    @Override public boolean createPlayerAccount(String n) { return true; }
    @Override public boolean createPlayerAccount(String n, String w) { return true; }

    // Vault "banks" (shared accounts) are not used by Towny; not supported.
    @Override public EconomyResponse createBank(String n, String p) { return fail("Banks not supported"); }
    @Override public EconomyResponse createBank(String n, OfflinePlayer p) { return fail("Banks not supported"); }
    @Override public EconomyResponse deleteBank(String n) { return fail("Banks not supported"); }
    @Override public EconomyResponse bankBalance(String n) { return fail("Banks not supported"); }
    @Override public EconomyResponse bankHas(String n, double a) { return fail("Banks not supported"); }
    @Override public EconomyResponse bankWithdraw(String n, double a) { return fail("Banks not supported"); }
    @Override public EconomyResponse bankDeposit(String n, double a) { return fail("Banks not supported"); }
    @Override public EconomyResponse isBankOwner(String n, String p) { return fail("Banks not supported"); }
    @Override public EconomyResponse isBankOwner(String n, OfflinePlayer p) { return fail("Banks not supported"); }
    @Override public EconomyResponse isBankMember(String n, String p) { return fail("Banks not supported"); }
    @Override public EconomyResponse isBankMember(String n, OfflinePlayer p) { return fail("Banks not supported"); }
    @Override public List<String> getBanks() { return new ArrayList<>(); }
}
