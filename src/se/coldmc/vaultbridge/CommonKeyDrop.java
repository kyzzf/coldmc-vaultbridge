package se.coldmc.vaultbridge;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The Common Crate key is a rare monster drop (the owner, 2026-10-27: "Rare monster drop"). Any hostile
 * monster a player kills has common-key.chance percent to drop one. A player has to make the kill,
 * in survival or adventure and inside a profile, and mobs from a spawner never drop it, so a mob
 * farm or a zombie burning in daylight mints nothing. This is not in MMOItems drops.yml because
 * those tables also roll when no player killed the mob.
 */
final class CommonKeyDrop implements Listener {
    private final JavaPlugin plugin;
    private final EconomyBridge eco;
    private final double chance;
    private final String type, id, message;

    CommonKeyDrop(JavaPlugin plugin, EconomyBridge eco) {
        this.plugin = plugin; this.eco = eco;
        FileConfiguration c = plugin.getConfig();
        chance = c.getDouble("common-key.chance", 1.0);
        type = c.getString("common-key.type", "CRATE_KEY");
        id = c.getString("common-key.id", "KEY_COMMON");
        message = c.getString("common-key.message", "&8[&dɢᴀɪᴀ&8] &7Something glints among the remains.");
        plugin.getLogger().info("Common Crate key: " + chance + "% from hostile monsters a player kills");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player p = dead.getKiller();
        if (p == null || !(dead instanceof Enemy) || dead instanceof Player) return;
        if (dead.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) return;
        GameMode g = p.getGameMode();
        if ((g != GameMode.SURVIVAL && g != GameMode.ADVENTURE) || !eco.inProfile(p)) return;
        if (ThreadLocalRandom.current().nextDouble() * 100 >= chance) return;
        ItemStack key = MMOItems.plugin.getItem(Type.get(type), id);
        if (key == null) {
            plugin.getLogger().warning("Common Crate key " + type + ":" + id + " did not build");
            return;
        }
        e.getDrops().add(key);
        if (!message.isEmpty()) p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
