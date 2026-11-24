package se.coldmc.vaultbridge;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tab completion for /warp (the owner, 2026-10-09: "add autocomplete"). CoreTools runs /warp itself
 * (EventsManager/coldmc-warps.yml cancels the typed command first), but CoreTools cannot offer
 * suggestions, so this registers /warp only to complete it: warp names for cmc.admin, read from
 * the generated plugins/CoreTools/Scripts/coldmc_warps.yml (warp_<name> scripts), then players.
 */
final class WarpCompleter implements TabExecutor {
    private final File scripts;
    private long loadedAt = -1;
    private List<String> warps = new ArrayList<>();

    WarpCompleter(Plugin plugin) {
        this.scripts = new File(plugin.getDataFolder().getParentFile(), "CoreTools/Scripts/coldmc_warps.yml");
    }

    private synchronized List<String> warps() {
        if (scripts.lastModified() != loadedAt) {
            loadedAt = scripts.lastModified();
            List<String> w = new ArrayList<>();
            for (String k : YamlConfiguration.loadConfiguration(scripts).getKeys(false))
                if (k.startsWith("warp_")) w.add(k.substring(5));
            Collections.sort(w);
            warps = w;
        }
        return warps;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] args) {
        // Only reached when CoreTools did not take the command (e.g. from the console).
        if (args.length >= 2) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "core-cast warp_" + args[0].toLowerCase() + " " + args[1]);
            return true;
        }
        s.sendMessage("Usage: warp <name> <player>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (s instanceof Player && !s.hasPermission("cmc.admin")) return out;
        String typed = args[args.length - 1].toLowerCase();
        if (args.length == 1) {
            for (String w : warps()) if (w.startsWith(typed) || w.contains("_" + typed)) out.add(w);
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(typed)) out.add(p.getName());
        }
        return out;
    }
}
