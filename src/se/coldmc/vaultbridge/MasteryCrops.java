package se.coldmc.vaultbridge;

import net.momirealms.customcrops.api.event.DropItemActionEvent;
import net.momirealms.customcrops.api.event.QualityCropActionEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Bountiful Harvest for CustomCrops. A crop's break event runs a drop-item action per drop (seeds
 * and produce alike) and DropItemActionEvent fires for each; QualityCropActionEvent is the
 * quality-tier variant. Seeds are never doubled and only items named after a crop count. The
 * roll is made once per harvest (player + block, this tick) and shared by every drop of it, and
 * whichever of the two events arrives first owns the harvest so it cannot be doubled twice.
 */
final class MasteryCrops implements Listener {
    private record Key(UUID player, UUID world, int x, int y, int z) { }

    private static final class Harvest {
        final int extras; final Class<?> owner;
        Harvest(int extras, Class<?> owner) { this.extras = extras; this.owner = owner; }
    }

    private final JavaPlugin plugin;
    private final MasteryLevels levels;
    private final Mastery harvest;
    private final Set<String> crops = new TreeSet<>();
    private final Map<Key, Harvest> rolled = new HashMap<>();

    MasteryCrops(JavaPlugin plugin, MasteryLevels levels) {
        this.plugin = plugin; this.levels = levels;
        harvest = Mastery.read(plugin.getConfig(), "bountiful-harvest", "farming", 2, 8, 4, 1, 24);
        crops.addAll(cropNames(plugin));
        plugin.getLogger().info("Bountiful Harvest also covers CustomCrops harvests (" + crops.size() + " crops)");
    }

    /** Top-level keys of every plugins/CustomCrops/contents/crops/*.yml (one crop per key). */
    static Set<String> cropNames(JavaPlugin plugin) {
        Set<String> out = new TreeSet<>();
        Path dir = new File(plugin.getDataFolder().getParentFile(), "CustomCrops/contents/crops").toPath();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
                for (String k : YamlConfiguration.loadConfiguration(p.toFile()).getKeys(false)) out.add(k.toLowerCase(Locale.ROOT));
            });
        } catch (IOException ignored) { }
        return out;
    }

    private boolean produce(String id) {
        if (id == null) return false;
        String s = id.toLowerCase(Locale.ROOT);
        if (s.contains("seed")) return false;
        for (String c : crops) if (s.contains(c)) return true;
        return false;
    }

    /** Extras for this harvest, or 0 when someone else's event already owns it. */
    private int extras(Player p, Location at, Class<?> kind) {
        Block b = at.getBlock();
        Key k = new Key(p.getUniqueId(), b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        Harvest h = rolled.get(k);
        if (h == null) {
            h = new Harvest(MasteryLevels.extras(levels.value(p, harvest)), kind);
            rolled.put(k, h);
            final Key key = k;
            plugin.getServer().getScheduler().runTask(plugin, () -> rolled.remove(key));
        }
        return h.owner == kind ? h.extras : 0;
    }

    private static void spill(Location at, List<ItemStack> items, int extras) {
        Location c = at.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (int i = 0; i < extras; i++)
            for (ItemStack s : items) if (s != null && !s.getType().isAir()) c.getWorld().dropItemNaturally(c, s.clone());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(DropItemActionEvent e) {
        if (!(e.context().holder() instanceof Player p) || !produce(e.droppedItemID())) return;
        if (e.item() == null || e.location() == null || !levels.eligible(p)) return;
        int extras = extras(p, e.location(), DropItemActionEvent.class);
        if (extras > 0) spill(e.location(), List.of(e.item()), extras);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuality(QualityCropActionEvent e) {
        if (!(e.context().holder() instanceof Player p) || e.location() == null || e.items() == null || e.items().isEmpty()) return;
        if (!levels.eligible(p)) return;
        int extras = extras(p, e.location(), QualityCropActionEvent.class);
        if (extras > 0) spill(e.location(), new ArrayList<>(e.items()), extras);
    }
}
