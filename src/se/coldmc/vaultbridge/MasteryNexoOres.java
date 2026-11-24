package se.coldmc.vaultbridge;

import com.nexomc.nexo.api.events.custom_block.NexoCustomBlockDropLootEvent;
import com.nexomc.nexo.utils.drops.DroppedLoot;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Locale;

/**
 * Lucky Miner for ColdMC's custom ores. Nexo spawns a custom block's drops itself (no
 * BlockDropItemEvent), then announces them with a drop-loot event; each of those loots is dropped
 * again here, with the same extra-copies rule, for blocks whose id has the word "ore" in it.
 */
final class MasteryNexoOres implements Listener {
    private final MasteryLevels levels;
    private final MasteryDrops drops;
    private final Mastery miner;
    private final String word;

    MasteryNexoOres(JavaPlugin plugin, MasteryLevels levels, MasteryDrops drops) {
        this.levels = levels; this.drops = drops;
        FileConfiguration c = plugin.getConfig();
        miner = Mastery.read(c, "lucky-miner", "mining", 2, 4, 2, 1, 50);
        word = c.getString("abilities.nexo-ore-word", "ore").toLowerCase(Locale.ROOT);
        plugin.getLogger().info("Lucky Miner also doubles Nexo custom ores (id word \"" + word + "\")");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoot(NexoCustomBlockDropLootEvent e) {
        Player p = e.getPlayer();
        if (p == null || e.getMechanic() == null) return;
        String id = e.getMechanic().getItemID();
        if (id == null || !Arrays.asList(id.toLowerCase(Locale.ROOT).split("_")).contains(word)) return;
        if (!levels.eligible(p)) return;
        Block b = e.getBlock();
        if (drops.placed(b)) return;
        int extras = MasteryLevels.extras(levels.value(p, miner));
        if (extras <= 0) return;
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        for (int i = 0; i < extras; i++)
            for (DroppedLoot dl : e.getLoots()) if (dl.amount() > 0) dl.loot().dropNaturally(at, dl.amount());
    }
}
