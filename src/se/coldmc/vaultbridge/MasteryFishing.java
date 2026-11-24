package se.coldmc.vaultbridge;

import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.event.FishingResultEvent;
import net.momirealms.customfishing.api.mechanic.MechanicType;
import net.momirealms.customfishing.api.mechanic.action.ActionTrigger;
import net.momirealms.customfishing.api.mechanic.context.Context;
import net.momirealms.customfishing.api.mechanic.context.ContextKeys;
import net.momirealms.customfishing.api.mechanic.effect.Effect;
import net.momirealms.customfishing.api.mechanic.fishing.CustomFishingHook;
import net.momirealms.customfishing.api.mechanic.fishing.FishingGears;
import net.momirealms.customfishing.api.mechanic.loot.Loot;
import net.momirealms.customfishing.api.mechanic.loot.LootType;
import org.bukkit.Statistic;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fishing mastery on CustomFishing catches (it does not fire PlayerFishEvent).
 *  - Lucky Catch: extra copies of the catch, by raising the catch amount. The hook re-reads the
 *    amount after FishingResultEvent and runs the whole catch (item, XP, stats) once per copy.
 *  - Treasure Hunter / Epic Catch: a chance for one bonus catch from the silver_star / golden_star
 *    loot group, drawn (by weight) from what this water can give right now. The bonus is built and
 *    dropped like a normal catch, then its success actions run, which is where MMOCore fishing XP
 *    and any money come from.
 */
final class MasteryFishing implements Listener {
    private final JavaPlugin plugin;
    private final MasteryLevels levels;
    private final Mastery lucky, hunter, epic;
    private final String hunterGroup, epicGroup;
    private boolean warned;

    MasteryFishing(JavaPlugin plugin, MasteryLevels levels) {
        this.plugin = plugin; this.levels = levels;
        var c = plugin.getConfig();
        lucky = Mastery.read(c, "lucky-catch", "fishing", 2, 4, 2, 1, -1);
        hunter = Mastery.read(c, "treasure-hunter", "fishing", 3, 1, 0.5, 2, -1);
        epic = Mastery.read(c, "epic-catch", "fishing", 4, 0.3, 0.25, 2, -1);
        hunterGroup = c.getString("abilities.treasure-hunter-group", "silver_star");
        epicGroup = c.getString("abilities.epic-catch-group", "golden_star");
        plugin.getLogger().info("Fishing mastery: lucky catch, treasure hunter (" + hunterGroup + "), epic catch (" + epicGroup + ")");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCatch(FishingResultEvent e) {
        if (e.getResult() != FishingResultEvent.Result.SUCCESS) return;
        Loot loot = e.getLoot();
        if (loot == null || loot.type() != LootType.ITEM) return;
        Player p = e.getPlayer();
        if (!levels.eligible(p)) return;
        try {
            int extras = MasteryLevels.extras(levels.value(p, lucky));
            if (extras > 0) e.getContext().arg(ContextKeys.AMOUNT, e.getAmount() + extras);
            if (MasteryLevels.roll(levels.value(p, hunter))) bonus(e, p, hunterGroup);
            if (MasteryLevels.roll(levels.value(p, epic))) bonus(e, p, epicGroup);
        } catch (RuntimeException | LinkageError ex) {
            if (!warned) { warned = true; plugin.getLogger().warning("Fishing mastery failed (shown once): " + ex); }
        }
    }

    /** One bonus catch from the given loot group, if this water has one. */
    private void bonus(FishingResultEvent e, Player p, String group) {
        BukkitCustomFishingPlugin cf = BukkitCustomFishingPlugin.getInstance();
        Context<Player> ctx = e.getContext();
        Effect effect = ctx.arg(ContextKeys.EFFECT);
        CustomFishingHook hook = cf.getFishingManager().getFishHook(p).orElse(null);
        if (effect == null || hook == null || e.getFishHook() == null) return;

        List<Loot> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        for (Map.Entry<String, Double> en : cf.getLootManager().getWeightedLoots(effect, ctx, false).entrySet()) {
            if (en.getValue() == null || en.getValue() <= 0) continue;
            Loot l = cf.getLootManager().getLoot(en.getKey()).orElse(null);
            if (l == null || l.type() != LootType.ITEM || l.lootGroup() == null || !Arrays.asList(l.lootGroup()).contains(group)) continue;
            pool.add(l); weights.add(en.getValue()); total += en.getValue();
        }
        if (pool.isEmpty()) return;
        double pick = ThreadLocalRandom.current().nextDouble() * total;
        Loot chosen = pool.get(pool.size() - 1);
        for (int i = 0; i < pool.size(); i++) { pick -= weights.get(i); if (pick < 0) { chosen = pool.get(i); break; } }

        // A fresh context for the bonus: the catch's own, retargeted at the bonus loot.
        Context<Player> bc = Context.player(p).combine(ctx);
        bc.remove(ContextKeys.SIZE);
        bc.remove(ContextKeys.PRICE);
        bc.remove(ContextKeys.IS_NEW_SIZE_RECORD);
        bc.arg(ContextKeys.ID, chosen.id()).arg(ContextKeys.NICK, chosen.nick()).arg(ContextKeys.LOOT, LootType.ITEM).arg(ContextKeys.AMOUNT, 1);

        ItemStack rod = hook.gears().getItem(FishingGears.GearType.ROD).stream().findAny().orElseThrow().right();
        FishHook hookEntity = e.getFishHook();
        if (chosen.toInventory().evaluate(bc) > 0) {
            ItemStack built = cf.getItemManager().getItemLoot(bc, rod, hookEntity);
            if (built == null || built.getType().isAir()) return;
            p.getInventory().addItem(built).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        } else {
            Item dropped = cf.getItemManager().dropItemLoot(bc, rod, hookEntity);
            if (dropped == null) return;
            dropped.setInvulnerable(true);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (dropped.isValid()) dropped.setInvulnerable(false); }, 20L);
        }
        final Loot caught = chosen;
        if (!caught.disableStats())
            cf.getStorageManager().getOnlineUser(p.getUniqueId()).ifPresent(u -> u.statistics().addAmount(caught.statisticKey().amountKey(), 1));
        p.setStatistic(Statistic.FISH_CAUGHT, p.getStatistic(Statistic.FISH_CAUGHT) + 1);
        cf.getEventManager().trigger(bc, chosen.id(), MechanicType.LOOT, ActionTrigger.SUCCESS);
    }
}
