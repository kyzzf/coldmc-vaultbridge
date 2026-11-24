package se.coldmc.vaultbridge;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mastery abilities that act on a broken vanilla block (BlockDropItemEvent):
 *  - Lucky Miner (ores), Lumberjack (logs), Bigger Scoop (diggables), Bountiful Harvest (ripe
 *    crops and stalks): extra copies of everything the block dropped.
 *  - Metal Detector / Lucky Spades: a chance for one item from the rare / epic digging pool.
 * Blocks a player placed never count: MMOCore stamps "player_placed" metadata on placement and
 * removes it on break, so it is read in BlockBreakEvent at LOWEST and remembered for the tick.
 * Ripe crops are the exception (see onDrop). Extra items go into the event's own list: Leaf spawns
 * every not-yet-valid Item in it after the event, and drops none of them if it is cancelled.
 */
final class MasteryDrops implements Listener {
    private record Key(UUID world, int x, int y, int z) { }

    private record Entry(Material item, int min, int max, Set<String> groups) { }

    private static final Set<Material> CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH,
            Material.TORCHFLOWER_CROP, Material.PITCHER_CROP);
    private static final Set<Material> STALKS = EnumSet.of(Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE,
            Material.CACTUS, Material.BAMBOO, Material.KELP, Material.KELP_PLANT);
    private static final Set<Material> SCOOP = EnumSet.of(Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT,
            Material.PODZOL, Material.ROOTED_DIRT, Material.MYCELIUM, Material.SAND, Material.RED_SAND,
            Material.GRAVEL, Material.CLAY, Material.SOUL_SAND, Material.SOUL_SOIL, Material.MUD,
            Material.MUDDY_MANGROVE_ROOTS);
    // The treasure group of each block; the soul/mud blocks have none, so they never yield treasure.
    private static final Map<Material, String> TREASURE = new EnumMap<>(Material.class);
    static {
        for (Material m : new Material[] {Material.DIRT, Material.GRASS_BLOCK, Material.MYCELIUM, Material.COARSE_DIRT,
                Material.PODZOL, Material.ROOTED_DIRT}) TREASURE.put(m, "dirts");
        TREASURE.put(Material.SAND, "sand"); TREASURE.put(Material.RED_SAND, "sand");
        TREASURE.put(Material.GRAVEL, "gravel"); TREASURE.put(Material.CLAY, "clay");
    }

    private final JavaPlugin plugin;
    private final MasteryLevels levels;
    private final Mastery miner, jack, scoop, harvest, detector, spades;
    private final List<Entry> rare, epic;
    private final Set<Key> placed = new HashSet<>();

    MasteryDrops(JavaPlugin plugin, MasteryLevels levels) {
        this.plugin = plugin; this.levels = levels;
        FileConfiguration c = plugin.getConfig();
        miner = Mastery.read(c, "lucky-miner", "mining", 2, 4, 2, 1, 50);
        jack = Mastery.read(c, "lumberjack", "foraging", 2, 4, 2, 1, 50);
        scoop = Mastery.read(c, "bigger-scoop", "excavation", 5, 3, 3, 5, -1);
        harvest = Mastery.read(c, "bountiful-harvest", "farming", 2, 8, 4, 1, 24);
        detector = Mastery.read(c, "metal-detector", "excavation", 2, 0.5, 0.4, 5, -1);
        spades = Mastery.read(c, "lucky-spades", "excavation", 6, 0.1, 0.05, 5, -1);
        rare = pool(c, "abilities.rare-pool");
        epic = pool(c, "abilities.epic-pool");
        plugin.getLogger().info("Mastery drops: lucky miner, lumberjack, bigger scoop, bountiful harvest, metal detector, lucky spades ("
                + rare.size() + " rare / " + epic.size() + " epic treasures)");
    }

    private List<Entry> pool(FileConfiguration c, String path) {
        List<Entry> out = new ArrayList<>();
        for (Map<?, ?> m : c.getMapList(path)) {
            Material mat = Material.matchMaterial(String.valueOf(m.get("item")));
            if (mat == null) { plugin.getLogger().warning("Unknown treasure item in " + path + ": " + m.get("item")); continue; }
            int min = m.get("min") instanceof Number n ? n.intValue() : 1;
            int max = m.get("max") instanceof Number n ? n.intValue() : min;
            Set<String> groups = new HashSet<>();
            if (m.get("groups") instanceof List<?> l) for (Object o : l) groups.add(String.valueOf(o).toLowerCase(Locale.ROOT));
            if (groups.isEmpty()) groups.add("any");
            out.add(new Entry(mat, Math.max(1, min), Math.max(min, max), groups));
        }
        return out;
    }

    private static Key key(Block b) { return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()); }

    /** True when the block at this spot was placed by a player (valid through the whole break tick). */
    boolean placed(Block b) { return placed.contains(key(b)) || b.hasMetadata("player_placed"); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!b.hasMetadata("player_placed")) return;
        Key k = key(b);
        if (placed.add(k)) plugin.getServer().getScheduler().runTask(plugin, () -> placed.remove(k));
    }

    static boolean ore(Material m) { return m == Material.ANCIENT_DEBRIS || m.name().endsWith("_ORE"); }

    private static boolean ripe(BlockState st) {
        Material m = st.getType();
        if (STALKS.contains(m)) return true;
        return CROPS.contains(m) && st.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        BlockState st = e.getBlockState();
        Material m = st.getType();
        Mastery luck = ore(m) ? miner : Tag.LOGS.isTagged(m) ? jack : SCOOP.contains(m) ? scoop : (CROPS.contains(m) || STALKS.contains(m)) && ripe(st) ? harvest : null;
        String group = TREASURE.get(m);
        if (luck == null && group == null) return;
        // A ripe crop counts even though it was planted: MMOCore tags planted seeds as player_placed
        // too, and a crop has to grow before it is ripe, so the tag means nothing there.
        boolean grownCrop = luck == harvest && CROPS.contains(m);
        if (!levels.eligible(p) || (!grownCrop && placed(e.getBlock()))) return;
        Location centre = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        if (luck != null) {
            int extras = MasteryLevels.extras(levels.value(p, luck));
            if (extras > 0 && !e.getItems().isEmpty()) {
                List<ItemStack> copies = new ArrayList<>();
                for (Item it : e.getItems()) copies.add(it.getItemStack().clone());
                for (int i = 0; i < extras; i++) for (ItemStack s : copies) add(e, centre, s.clone());
            }
        }
        if (group != null) {
            if (MasteryLevels.roll(levels.value(p, detector))) treasure(e, centre, rare, group);
            if (MasteryLevels.roll(levels.value(p, spades))) treasure(e, centre, epic, group);
        }
    }

    private void treasure(BlockDropItemEvent e, Location centre, List<Entry> pool, String group) {
        List<Entry> valid = new ArrayList<>();
        for (Entry en : pool) if (en.groups().contains("any") || en.groups().contains(group)) valid.add(en);
        if (valid.isEmpty()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Entry en = valid.get(r.nextInt(valid.size()));
        add(e, centre, new ItemStack(en.item(), r.nextInt(en.min(), en.max() + 1)));
    }

    /** Appends a new item to the event's list so it spawns with the rest; falls back to a plain drop. */
    private void add(BlockDropItemEvent e, Location centre, ItemStack stack) {
        World w = centre.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector v = new Vector(r.nextDouble(-0.1, 0.1), 0.2, r.nextDouble(-0.1, 0.1));
        try {
            Item it = w.createEntity(centre, Item.class);
            it.setItemStack(stack);
            it.setVelocity(v);
            e.getItems().add(it);
        } catch (RuntimeException ex) {
            w.dropItemNaturally(centre, stack);
        }
    }
}
