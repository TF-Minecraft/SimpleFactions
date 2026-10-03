package net.tfminecraft.simplefactions.identity;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Notes a realm or guild leader's character shortly after they join, so the map learns
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
        refresh(player);
    }

    /**
     * Re-reads every realm and guild leader's character (or only `player`'s
     * when given) and, if any name changed, asks the map to ship nation.json
     * on its next cycle.
     */
    public static void refresh(String player) {
        boolean changed = false;
        for (Faction faction : FactionManager.factions) {
            if (faction == null) continue;
            if (player == null || player.equalsIgnoreCase(faction.getLeader())) {
                LeaderCharacters.Remembered remembered = LeaderCharacters.resolve(
                        faction.getLeader(), faction.getLeaderCharacter(), faction.getLeaderCharacterOf());
                changed |= !java.util.Objects.equals(remembered.name(), faction.getLeaderCharacter());
                faction.rememberLeaderCharacter(remembered.name(), remembered.player());
            }
            // Guild leaders too; a realm's own guild follows the realm above.
            for (Guild guild : faction.getGuildHandler().getGuilds()) {
                if (guild == null || guild.isBase()) continue;
                if (player != null && !player.equalsIgnoreCase(guild.getLeader())) continue;
                LeaderCharacters.Remembered remembered = LeaderCharacters.resolve(
                        guild.getLeader(), guild.getLeaderCharacter(), guild.getLeaderCharacterOf());
                changed |= !java.util.Objects.equals(remembered.name(), guild.getLeaderCharacter());
                guild.rememberLeaderCharacter(remembered.name(), remembered.player());
            }
        }
        if (changed && FactionManager.getMap() != null) {
            FactionManager.getMap().markLeaderNamesChanged();
        }
    }
}
