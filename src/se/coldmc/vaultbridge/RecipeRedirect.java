package se.coldmc.vaultbridge;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

/**
 * The inventory's 2x2 grid is ItemJoin's tab strip (Flake, 2026-10-30). ItemJoin keeps buttons in
 * CRAFTING[0..3] (plugins/ItemJoin/items.yml), so only one grid slot is free and nothing can be
 * crafted there. The recipe book still offers 2x2 recipes, and a click on one emptied the result
 * (the owner, 2026-10-30: "when I click it the result crafting recipe disappears and it crafts to
 * nothing"). A click there now opens the 3x3 crafting table with the recipe laid in, taken from the
 * player's inventory: one set, or as many as fit when the click was a shift-click (make all).
 * Clicks inside a real or /craft table are left to vanilla.
 */
final class RecipeRedirect implements Listener {
    private final Plugin plugin;
    private final CustomItemGuard guard;   // null when neither MMOItems nor Nexo is loaded

    RecipeRedirect(Plugin plugin, CustomItemGuard guard) { this.plugin = plugin; this.guard = guard; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBookClick(PlayerRecipeBookClickEvent e) {
        Player p = e.getPlayer();
        if (p.getOpenInventory().getType() != InventoryType.CRAFTING) return;
        e.setCancelled(true);
        Recipe r = Bukkit.getRecipe(e.getRecipe());
        boolean all = e.isMakeAll();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            InventoryView v = p.openWorkbench(null, true);
            if (v != null && v.getTopInventory() instanceof CraftingInventory ci && r != null) fill(p, ci, r, all);
        });
    }

    private static RecipeChoice[] layout(Recipe r) {
        RecipeChoice[] at = new RecipeChoice[9];
        if (r instanceof ShapedRecipe s) {
            String[] shape = s.getShape();
            Map<Character, RecipeChoice> m = s.getChoiceMap();
            for (int row = 0; row < shape.length && row < 3; row++)
                for (int col = 0; col < shape[row].length() && col < 3; col++)
                    at[row * 3 + col] = m.get(shape[row].charAt(col));
        } else if (r instanceof ShapelessRecipe s) {
            List<RecipeChoice> l = s.getChoiceList();
            for (int i = 0; i < l.size() && i < 9; i++) at[i] = l.get(i);
        } else return null;
        return at;
    }

    private void fill(Player p, CraftingInventory ci, Recipe r, boolean all) {
        RecipeChoice[] at = layout(r);
        if (at == null) return;
        ItemStack[] inv = p.getInventory().getStorageContents();
        ItemStack[] grid = new ItemStack[9];
        for (int set = 0; set < (all ? 64 : 1); set++) {
            ItemStack[] tryInv = copy(inv);
            ItemStack[] tryGrid = copy(grid);
            boolean ok = true;
            for (int i = 0; i < 9 && ok; i++) {
                if (at[i] == null) continue;
                ok = false;
                for (int j = 0; j < tryInv.length; j++) {
                    ItemStack it = tryInv[j];
                    if (it == null || it.getType().isAir() || !at[i].test(it)) continue;
                    if (guard != null && guard.custom(it)) continue;
                    ItemStack g = tryGrid[i];
                    if (g != null && (!g.isSimilar(it) || g.getAmount() >= g.getMaxStackSize())) continue;
                    if (g == null) { g = it.clone(); g.setAmount(1); tryGrid[i] = g; } else g.setAmount(g.getAmount() + 1);
                    if (it.getAmount() <= 1) tryInv[j] = null; else it.setAmount(it.getAmount() - 1);
                    ok = true;
                    break;
                }
            }
            if (!ok) break;
            inv = tryInv;
            grid = tryGrid;
        }
        p.getInventory().setStorageContents(inv);
        ci.setMatrix(grid);
    }

    private static ItemStack[] copy(ItemStack[] a) {
        ItemStack[] b = new ItemStack[a.length];
        for (int i = 0; i < a.length; i++) b[i] = a[i] == null ? null : a[i].clone();
        return b;
    }
}
