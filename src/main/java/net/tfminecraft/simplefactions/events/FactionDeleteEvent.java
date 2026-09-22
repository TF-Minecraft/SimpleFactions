package net.tfminecraft.simplefactions.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import net.tfminecraft.simplefactions.objects.Faction;

public class FactionDeleteEvent extends Event implements Cancellable{
	private static final HandlerList HANDLERS = new HandlerList();
    private boolean isCancelled;
    private final Player p;
    private final Faction f;
	@Override
	public boolean isCancelled() {
		return this.isCancelled;
	}

	@Override
	public void setCancelled(boolean arg0) {
		this.isCancelled = arg0;
		
	}
	public FactionDeleteEvent(Player p, Faction f) {
    	this.p = p;
    	this.f = f;
    }

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}
	public static HandlerList getHandlerList() {
        return HANDLERS;
    }

	public Player getCreator() {
        return this.p;
    }
    public Faction getFaction() {
    	return this.f;
    }
}
