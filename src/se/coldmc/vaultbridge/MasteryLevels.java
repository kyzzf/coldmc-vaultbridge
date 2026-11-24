package se.coldmc.vaultbridge;

import net.Indyuce.mmocore.api.player.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared rules for the mastery abilities: who may roll, what level they are, how a percent
 * becomes copies. Needs MMOCore; only constructed when it is present.
 */
final class MasteryLevels {
    private final EconomyBridge eco;

    MasteryLevels(EconomyBridge eco) { this.eco = eco; }

    /** Survival or adventure, and inside an MMOProfiles profile (not on the select screen). */
    boolean eligible(Player p) {
        GameMode g = p.getGameMode();
        return (g == GameMode.SURVIVAL || g == GameMode.ADVENTURE) && eco.inProfile(p);
    }

    /** MMOCore profession level of the active profile; 0 when MMOCore has no data for the player. */
    int level(Player p, String profession) {
        try {
            return PlayerData.has(p) ? PlayerData.get(p).getCollectionSkills().getLevel(profession) : 0;
        } catch (RuntimeException | LinkageError ex) {
            return 0;
        }
    }

    /** The ability's percent for this player, 0 when not eligible or still locked. */
    double value(Player p, Mastery m) { return eligible(p) ? m.value(level(p, m.profession)) : 0; }

    /** floor(value/100) guaranteed extras plus one more with chance (value mod 100)%. */
    static int extras(double value) {
        if (value <= 0) return 0;
        int n = (int) (value / 100);
        if (ThreadLocalRandom.current().nextDouble() * 100 < value - n * 100.0) n++;
        return n;
    }

    /** A plain percent roll. */
    static boolean roll(double percent) { return percent > 0 && ThreadLocalRandom.current().nextDouble() * 100 < percent; }
}
