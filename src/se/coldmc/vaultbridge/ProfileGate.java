package se.coldmc.vaultbridge;

import fr.phoenixdevt.profiles.event.ProfileSelectedEvent;
import fr.phoenixdevt.profiles.event.ProfileUnloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Who is inside an MMOProfiles profile. Kept in its own class so the bridge still loads
 * without MMOProfiles; only registered when it is present.
 */
final class ProfileGate implements Listener {
    private final EconomyBridge eco;
    private final EraTracker eras;

    ProfileGate(EconomyBridge eco, EraTracker eras) { this.eco = eco; this.eras = eras; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSelected(ProfileSelectedEvent e) {
        eco.profileEntered(e.getPlayer(), e.getProfile().getUniqueId());
        eras.profileEntered(e.getPlayer(), e.getProfile().getUniqueId());
        CommandRefresh.later(e.getPlayer(), 20);
    }

    // Before the profile's data is saved and dropped, so the snapshot is still that profile's.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUnload(ProfileUnloadEvent e) {
        eco.profileLeft(e.getPlayer());
        eras.profileLeft(e.getPlayer());
    }
}
