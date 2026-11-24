package se.coldmc.vaultbridge;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ColdMC's Vault bridge (Flake, 2026-10-09).
 *  - Chat: CoreTools' CorePerms registers no Vault chat provider, so %vault_prefix% and
 *    TownyChat's {permprefix} were empty. ChatBridge answers with %coretools_perms_prefix%.
 *  - Economy: CoreTools' Vault economy only knows online players (Bukkit.getPlayer), so a
 *    deposit into a Towny town/nation bank threw a NullPointerException. EconomyBridge passes
 *    online players through to CoreTools, books offline players in a ledger that is settled
 *    when they next load a profile, and keeps every other account in banks.yml. A player still
 *    on the MMOProfiles select screen counts as offline (ProfileGate) - 2026-10-10.
 *  - Permissions: Vault's SuperPerms has no groups, which broke Towny's prefix lookup.
 *    PermBridge reads the CoreTools core-perms groups.
 *  - /cmchold: console commands held until the player is inside a profile (HeldCommands).
 *  - /warp tab completion for admins (WarpCompleter); CoreTools still runs the command.
 *  - Eras (2026-10-23): EraTracker keeps each profile's era, %coldmc_era% (EraExpansion) gates
 *    pouches, crates and shops, and NexoPacks turns it into the player's Nexo multipack.
 *  - CustomItemGuard (2026-10-23): MMOItems and Nexo items do nothing in vanilla crafting,
 *    smithing or cooking - they belong in custom stations.
 *  - Mastery abilities (2026-10-27): Mastery* and Geneticist give the ten MMOCore mastery
 *    abilities their effects (extra drops, digging treasure, fishing bonuses, plant saturation).
 *  - CommonKeyDrop (2026-10-27): the Common Crate key drops from hostile monsters players kill.
 *  - CommandRefresh (2026-10-30): resends the command list once CoreTools has attached the rank.
 *  - RecipeRedirect (2026-10-30): a recipe-book click in the inventory (whose 2x2 grid is
 *    ItemJoin's tab strip) opens the 3x3 crafting table with the recipe laid in.
 *  - RecipeUnlock (2026-11-02): every vanilla recipe is in the recipe book at join.
 */
public final class ColdmcVaultBridge extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PermBridge perms = new PermBridge(this);
        EconomyBridge eco = new EconomyBridge(this);
        getServer().getServicesManager().register(Permission.class, perms, this, ServicePriority.Highest);
        getServer().getServicesManager().register(Chat.class, new ChatBridge(perms), this, ServicePriority.Highest);
        getServer().getServicesManager().register(Economy.class, eco, this, ServicePriority.Highest);
        getServer().getPluginManager().registerEvents(eco, this);
        getServer().getPluginManager().registerEvents(new CommandRefresh(this), this);
        boolean profiles = getServer().getPluginManager().getPlugin("MMOProfiles") != null;
        EraTracker eras = new EraTracker(this, profiles);
        if (profiles) {
            eco.gateProfiles();
            getServer().getPluginManager().registerEvents(new ProfileGate(eco, eras), this);
        }
        if (getServer().getPluginManager().getPlugin("Nexo") != null) eras.setPackSync(new NexoPacks(this));
        getServer().getPluginManager().registerEvents(eras, this);
        if (getCommand("coldmcera") != null) { getCommand("coldmcera").setExecutor(eras); getCommand("coldmcera").setTabCompleter(eras); }
        new EraExpansion(eras).register();
        eras.start();
        CustomItemGuard guard = null;
        if (getServer().getPluginManager().getPlugin("MMOItems") != null || getServer().getPluginManager().getPlugin("Nexo") != null)
            getServer().getPluginManager().registerEvents(guard = new CustomItemGuard(this), this);
        getServer().getPluginManager().registerEvents(new RecipeRedirect(this, guard), this);
        getServer().getPluginManager().registerEvents(new RecipeUnlock(this), this);
        registerMastery(eco);
        if (getServer().getPluginManager().getPlugin("MMOItems") != null)
            getServer().getPluginManager().registerEvents(new CommonKeyDrop(this, eco), this);
        HeldCommands held = new HeldCommands(this, eco);
        eco.setHeld(held);
        if (getCommand("cmchold") != null) getCommand("cmchold").setExecutor(held);
        WarpCompleter warp = new WarpCompleter(this);
        if (getCommand("warp") != null) { getCommand("warp").setExecutor(warp); getCommand("warp").setTabCompleter(warp); }
        getLogger().info("Vault permissions (CoreTools groups), chat (%coretools_perms_prefix%) and economy (CoreTools + banks + offline ledger) registered");
    }

    /**
     * Mastery abilities (2026-10-27): extra drops, digging treasure, fishing bonuses, Geneticist.
     * None of MMOCore, Nexo, CustomCrops or CustomFishing is a softdepend, for the reason in
     * NexoPacks: CustomCrops and Nexo load after Towny, so declaring them is a load-order cycle.
     * The listeners only touch those plugins when an event fires, after every plugin has enabled.
     */
    private void registerMastery(EconomyBridge eco) {
        PluginManager pm = getServer().getPluginManager();
        if (pm.getPlugin("MMOCore") == null) return;
        MasteryLevels levels = new MasteryLevels(eco);
        MasteryDrops drops = new MasteryDrops(this, levels);
        pm.registerEvents(drops, this);
        if (pm.getPlugin("Nexo") != null) pm.registerEvents(new MasteryNexoOres(this, levels, drops), this);
        if (pm.getPlugin("CustomCrops") != null) pm.registerEvents(new MasteryCrops(this, levels), this);
        if (pm.getPlugin("CustomFishing") != null) pm.registerEvents(new MasteryFishing(this, levels), this);
        pm.registerEvents(new Geneticist(this, levels), this);
    }
}
