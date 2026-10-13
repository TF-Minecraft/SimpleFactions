package net.tfminecraft.simplefactions.events;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import net.tfminecraft.simplefactions.objects.Faction;

/**
 * Fired after a faction's leader changes, from {@code Faction.setLeader} and
 * {@code Faction.succeedDeadLeader}, the only two writers of the leader. Loading
 * saved factions does not fire it.
 */
public class FactionLeaderChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Faction faction;
    private final String previousLeader;
    private final String newLeader;

    public FactionLeaderChangedEvent(Faction faction, String previousLeader, String newLeader) {
        this.faction = faction;
        this.previousLeader = previousLeader;
        this.newLeader = newLeader;
    }

    /**
     * Fires the event on the main thread of a running server. A change made elsewhere is not
     * announced; the vehicle backstop and the next boot's reconcile catch its stamps.
     */
    public static void fire(Faction faction, String previousLeader, String newLeader) {
        if (java.util.Objects.equals(previousLeader, newLeader) || Bukkit.getServer() == null
                || Bukkit.getPluginManager() == null || !Bukkit.isPrimaryThread()) {
            return;
        }
        Bukkit.getPluginManager().callEvent(new FactionLeaderChangedEvent(faction, previousLeader, newLeader));
    }

    public Faction getFaction() {
        return faction;
    }

    /** The previous leader's member key, possibly null or blank. */
    public String getPreviousLeader() {
        return previousLeader;
    }

    /** The new leader's member key, possibly null or blank. */
    public String getNewLeader() {
        return newLeader;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
