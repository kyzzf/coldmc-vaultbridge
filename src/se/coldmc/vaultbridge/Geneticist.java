package se.coldmc.vaultbridge;

import com.nexomc.nexo.api.NexoItems;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Geneticist: plant-based food gives extra saturation, a percent of its own. The base is read
 * from the item's food component, so custom foods are right too. Plant foods are the vanilla
 * list below plus any MMOItems / Nexo item whose id contains the name of a CustomCrops crop.
 * The bonus lands one tick later, after the server has applied the meal.
 */
final class Geneticist implements Listener {
    private static final Set<Material> PLANT = EnumSet.of(Material.APPLE, Material.BREAD, Material.CARROT,
            Material.GOLDEN_CARROT, Material.POTATO, Material.BAKED_POTATO, Material.BEETROOT, Material.BEETROOT_SOUP,
            Material.MELON_SLICE, Material.SWEET_BERRIES, Material.GLOW_BERRIES, Material.PUMPKIN_PIE, Material.COOKIE,
            Material.DRIED_KELP, Material.MUSHROOM_STEW, Material.CHORUS_FRUIT);

    private final JavaPlugin plugin;
    private final MasteryLevels levels;
    private final Mastery gene;
    private final Set<String> crops;
    private final boolean nexo, mmo;

    Geneticist(JavaPlugin plugin, MasteryLevels levels) {
        this.plugin = plugin; this.levels = levels;
        gene = Mastery.read(plugin.getConfig(), "geneticist", "farming", 15, 1, 2, 5, -1);
        nexo = plugin.getServer().getPluginManager().getPlugin("Nexo") != null;
        mmo = plugin.getServer().getPluginManager().getPlugin("MMOItems") != null;
        crops = plugin.getServer().getPluginManager().getPlugin("CustomCrops") != null ? MasteryCrops.cropNames(plugin) : Set.of();
        plugin.getLogger().info("Geneticist: extra saturation from plant foods (" + crops.size() + " custom crop names)");
    }

    private boolean plant(ItemStack it) {
        if (PLANT.contains(it.getType())) return true;
        if (crops.isEmpty()) return false;
        String id = null;
        if (mmo) id = MMOItems.getID(it);
        if ((id == null || id.isEmpty()) && nexo) id = NexoItems.idFromItem(it);   // getID answers "", not null
        if (id != null && id.isEmpty()) id = null;
        if (id == null) return false;
        id = id.toLowerCase(Locale.ROOT);
        for (String c : crops) if (id.contains(c)) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        ItemStack it = e.getItem();
        if (!plant(it) || !levels.eligible(p)) return;
        FoodProperties food = it.getData(DataComponentTypes.FOOD);
        if (food == null) return;
        double extra = food.saturation() * gene.value(levels.level(p, gene.profession)) / 100.0;
        if (extra <= 0) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) p.setSaturation((float) Math.min(p.getFoodLevel(), p.getSaturation() + extra));
        });
    }
}
