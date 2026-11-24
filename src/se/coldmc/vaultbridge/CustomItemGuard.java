package se.coldmc.vaultbridge;

import com.nexomc.nexo.api.NexoItems;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Custom items only work inside custom stations (the owner, 2026-10-23: "steel crafted an iron
 * sword ... the use for nexo and mmoitems is ONLY inside of custom stations").
 *
 * MMOItems' own disable-interactions (craft/smelt/smith) cancels the click but still shows the
 * vanilla result, and knows nothing about Nexo items (a Nexo item is a plain vanilla material
 * with a nexo:id tag, so Steel counted as an iron ingot). This blanks the result of any vanilla
 * crafting-table or smithing recipe that has an MMOItem or Nexo item in it, and stops furnaces,
 * smokers, blast furnaces and campfires from cooking one. Recipes MMOItems registers itself
 * (namespace mmoitems) are left alone.
 *
 * CustomFishing catches are Nexo items by design and stay ordinary food - every id named as
 * `material: Nexo:<id>` under plugins/CustomFishing/contents is exempt, read at startup.
 */
final class CustomItemGuard implements Listener {
    private static final Pattern FISH = Pattern.compile("(?i)material:\\s*['\"]?nexo:([a-z0-9_]+)");
    private final Set<String> exempt = new HashSet<>();
    private final boolean nexo, mmo;

    CustomItemGuard(JavaPlugin plugin) {
        nexo = plugin.getServer().getPluginManager().getPlugin("Nexo") != null;
        mmo = plugin.getServer().getPluginManager().getPlugin("MMOItems") != null;
        Path dir = new File(plugin.getDataFolder().getParentFile(), "CustomFishing/contents").toPath();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.walk(dir)) {
                s.filter(p -> p.toString().endsWith(".yml")).forEach(p -> {
                    try {
                        Matcher m = FISH.matcher(Files.readString(p));
                        while (m.find()) exempt.add(m.group(1).toLowerCase(Locale.ROOT));
                    } catch (IOException ignored) { }
                });
            } catch (IOException ignored) { }
        }
        plugin.getLogger().info("Custom items blocked from vanilla crafting, smithing and cooking ("
                + exempt.size() + " fishing catches exempt)");
    }

    boolean custom(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        // getID() answers "" for a plain item, never null. Testing != null made every item count as
        // custom, so every vanilla craft, smith and cook failed from 2026-10-23 to 2026-11-02.
        if (mmo) {
            String mmoId = MMOItems.getID(it);
            if (mmoId != null && !mmoId.isEmpty()) return true;
        }
        if (nexo) {
            String id = NexoItems.idFromItem(it);
            return id != null && !exempt.contains(id);
        }
        return false;
    }

    private static boolean ownRecipe(Recipe r) {
        return r instanceof Keyed k && k.getKey().getNamespace().equals("mmoitems");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent e) {
        if (e.getRecipe() == null || ownRecipe(e.getRecipe())) return;
        for (ItemStack it : e.getInventory().getMatrix())
            if (custom(it)) { e.getInventory().setResult(null); return; }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmith(PrepareSmithingEvent e) {
        Recipe r = e.getInventory().getRecipe();
        if (r != null && ownRecipe(r)) return;
        for (ItemStack it : e.getInventory().getContents())
            if (custom(it)) { e.setResult(null); return; }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCook(BlockCookEvent e) {
        if (custom(e.getSource())) e.setCancelled(true);
    }
}
