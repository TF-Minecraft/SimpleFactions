package net.tfminecraft.simplefactions.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired once SimpleFactions has migrated its data to characters and started.
 * Before it, faction lookups answer nothing; see {@code SimpleFactionsApi.ready()}.
 */
public class SimpleFactionsReadyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
