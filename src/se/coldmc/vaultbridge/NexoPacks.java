package se.coldmc.vaultbridge;

import com.nexomc.nexo.NexoPlugin;
import com.nexomc.nexo.pack.multipack.PlayerPackData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Era -> Nexo multipack templates (config.yml eras.pack-templates, cumulative: era 2 also gets
 * era 1's packs). Nexo keeps a player's selection in their PDC (nexo:multipack_selections) and
 * every sendPack replaces the client's whole pack set, so writing the selection and resending
 * is all it takes - including for a profile switch to a LOWER era. Only resends on a change.
 * Kept in its own class so the bridge still loads without Nexo. Nexo is NOT a softdepend:
 * the bridge loads before Towny and Nexo after it, so declaring one is a load-order cycle that
 * stops the server (2026-10-23 test boot). Nothing here touches Nexo before a player joins.
 */
final class NexoPacks implements EraTracker.PackSync {
    private final JavaPlugin plugin;
    private final TreeMap<Integer, Set<String>> byEra = new TreeMap<>();

    NexoPacks(JavaPlugin plugin) {
        this.plugin = plugin;
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("eras.pack-templates");
        if (c != null) for (String k : c.getKeys(false))
            byEra.put(Integer.parseInt(k), new TreeSet<>(c.getStringList(k)));
    }

    @Override
    public void sync(Player p, int era, boolean resend) {
        Set<String> want = new TreeSet<>();
        for (Map.Entry<Integer, Set<String>> e : byEra.headMap(era, true).entrySet()) want.addAll(e.getValue());
        Set<String> have = new TreeSet<>();
        for (Map.Entry<String, Boolean> e : PlayerPackData.INSTANCE.getSelections(p).entrySet())
            if (Boolean.TRUE.equals(e.getValue())) have.add(e.getKey());
        if (want.equals(have)) return;
        Map<String, Boolean> sel = new HashMap<>();
        for (String id : want) sel.put(id, true);
        PlayerPackData.INSTANCE.setSelections(p, sel);
        plugin.getLogger().info("Packs: " + p.getName() + " era " + era + " -> " + (want.isEmpty() ? "main pack only" : want));
        if (resend) NexoPlugin.instance().packServer().sendPack(p);
    }
}
