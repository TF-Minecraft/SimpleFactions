package net.tfminecraft.simplefactions.identity;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Notes a realm leader's character shortly after they join, so the map learns
 * it even when the leader is never online at the moment the map exports. The
 * delay gives RPCharacters time to load the player's characters. The name is
 * kept on the faction and written out with its next save.
 */
public final class LeaderCharacterListener implements Listener {
    /** Five seconds: RPCharacters loads player data asynchronously on join. */
    private static final long DELAY_TICKS = 100L;

    private final Plugin plugin;

    public LeaderCharacterListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        String player = event.getPlayer().getName();
        Bukkit.getScheduler().runTaskLater(plugin, () -> rememberFor(player), DELAY_TICKS);
    }

    static void rememberFor(String player) {
        for (Faction faction : FactionManager.factions) {
            if (faction == null || !player.equalsIgnoreCase(faction.getLeader())) continue;
            LeaderCharacters.Remembered remembered = LeaderCharacters.resolve(
                    faction.getLeader(), faction.getLeaderCharacter(), faction.getLeaderCharacterOf());
            faction.rememberLeaderCharacter(remembered.name(), remembered.player());
        }
    }
}
