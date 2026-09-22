package me.Plugins.SimpleFactions.Events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerLeaveRegionEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final String regionId;
	private final String nextRegionId;

	public PlayerLeaveRegionEvent(Player player, String regionId, String nextRegionId) {
		this.player = player;
		this.regionId = regionId;
		this.nextRegionId = nextRegionId;
	}

	public Player getPlayer() {
		return player;
	}

	public String getRegionId() {
		return regionId;
	}

	public String getNextRegionId() {
		return nextRegionId;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
