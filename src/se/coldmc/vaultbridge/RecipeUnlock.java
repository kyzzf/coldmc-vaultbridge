package se.coldmc.vaultbridge;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Every vanilla recipe is in the recipe book from the first join (Flake, 2026-11-02). UltimateAdvancementAPI's
 * disable-vanilla-recipe-advancements stops playing from unlocking recipes (the owner's 2026-09-22
 * ruling, for the toasts), so the book stayed nearly empty. the owner, 2026-11-02: "Unlock all at join".
 * Only the minecraft namespace: MMOItems and Nexo recipes belong to their own stations. A recipe the
 * player already knows is skipped, so a toast can only come the first time.
 */
final class RecipeUnlock implements Listener {
    private final Plugin plugin;
    private List<NamespacedKey> vanilla;

    RecipeUnlock(Plugin plugin) { this.plugin = plugin; }

    private List<NamespacedKey> vanilla() {
        if (vanilla == null) {
            List<NamespacedKey> keys = new ArrayList<>();
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext())
                if (it.next() instanceof Keyed k && k.getKey().getNamespace().equals(NamespacedKey.MINECRAFT)) keys.add(k.getKey());
            vanilla = keys;
            plugin.getLogger().info("Recipe book: " + keys.size() + " vanilla recipes unlocked at join");
        }
        return vanilla;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) p.discoverRecipes(vanilla()); }, 40);
    }
}
