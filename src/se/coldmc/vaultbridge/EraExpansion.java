package se.coldmc.vaultbridge;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** %coldmc_era% (0-3) and %coldmc_era_name% - see EraTracker. */
final class EraExpansion extends PlaceholderExpansion {
    private final EraTracker eras;

    EraExpansion(EraTracker eras) { this.eras = eras; }

    @Override public String getIdentifier() { return "coldmc"; }
    @Override public String getAuthor() { return "Flake"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        Player p = player == null ? null : player.getPlayer();
        int e = eras.era(p);
        if (params.equalsIgnoreCase("era")) return String.valueOf(e);
        if (params.equalsIgnoreCase("era_name")) return EraTracker.NAMES[e];
        return null;
    }
}
