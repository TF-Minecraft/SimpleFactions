package me.Plugins.SimpleFactions.Events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerEnterRegionEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final String regionId;
	private final String regionName;
	private final String previousRegionId;

	public PlayerEnterRegionEvent(
			Player player,
			String regionId,
			String regionName,
			String previousRegionId) {
		this.player = player;
		this.regionId = regionId;
		this.regionName = regionName;
		this.previousRegionId = previousRegionId;
	}

	public Player getPlayer() {
		return player;
	}

	public String getRegionId() {
		return regionId;
	}

	public String getRegionName() {
		return regionName;
	}

	public String getPreviousRegionId() {
		return previousRegionId;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
