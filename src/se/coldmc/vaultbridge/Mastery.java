package se.coldmc.vaultbridge;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * One mastery ability's chance curve, read from the abilities: section of config.yml.
 * value (percent) = level >= unlock ? base + per * min(stepsCap, floor((level - unlock) / stepEvery)) : 0
 */
final class Mastery {
    final String profession;
    final int unlock, stepEvery, stepsCap; // stepsCap < 0 = unbounded
    final double base, per;

    private Mastery(String profession, int unlock, double base, double per, int stepEvery, int stepsCap) {
        this.profession = profession; this.unlock = unlock; this.base = base; this.per = per;
        this.stepEvery = Math.max(1, stepEvery); this.stepsCap = stepsCap;
    }

    /** Reads abilities.<key>; the defaults baked into the jar's config.yml fill any missing field. */
    static Mastery read(FileConfiguration c, String key, String profession, int unlock, double base, double per, int every, int cap) {
        String p = "abilities." + key + ".";
        String capText = String.valueOf(c.get(p + "steps-cap", cap < 0 ? "none" : cap));
        int capVal;
        try { capVal = capText.equalsIgnoreCase("none") ? -1 : Integer.parseInt(capText.trim()); } catch (NumberFormatException ex) { capVal = cap; }
        return new Mastery(c.getString(p + "profession", profession), c.getInt(p + "unlock", unlock),
                c.getDouble(p + "base", base), c.getDouble(p + "per", per), c.getInt(p + "step-every", every), capVal);
    }

    double value(int level) {
        if (level < unlock) return 0;
        int steps = (level - unlock) / stepEvery;
        if (stepsCap >= 0) steps = Math.min(stepsCap, steps);
        return base + per * steps;
    }
}
