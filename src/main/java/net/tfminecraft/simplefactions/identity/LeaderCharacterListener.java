package net.tfminecraft.simplefactions.identity;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import net.tfminecraft.tlibs.character.CharacterSessionStartEvent;
import net.tfminecraft.tlibs.character.Characters;

import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;

/** Refreshes map leader names when a shared character session is ready. */
public final class LeaderCharacterListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCharacterStart(CharacterSessionStartEvent event) {
        var player = event.getPlayer();
        if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
                || !event.getSession().ref().ownerId().equals(player.getUniqueId())
                || !Characters.isCurrent(event.getSession())) return;
        refresh(Members.key(event.getSession().ref()));
    }

    /**
     * Re-reads every realm and guild leader's character (or only the
     * character with member key `player` when given) and, if any name
     * changed, asks the map to ship nation.json on its next cycle.
     */
    public static void refresh(String player) {
        boolean changed = false;
        for (Faction faction : FactionManager.factions) {
            if (faction == null) continue;
            if (player == null || player.equals(faction.getLeader())) {
                changed |= LeaderCharacters.refresh(faction);
            }
            // Guild leaders too; a realm's own guild follows the realm above.
            for (Guild guild : faction.getGuildHandler().getGuilds()) {
                if (guild == null || guild.isBase()) continue;
                if (player != null && !player.equals(guild.getLeader())) continue;
                changed |= LeaderCharacters.refresh(guild);
            }
        }
        if (changed && FactionManager.getMap() != null) {
            FactionManager.getMap().markLeaderNamesChanged();
        }
    }
}
