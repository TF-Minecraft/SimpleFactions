package me.Plugins.SimpleFactions.Events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerTitleLeaveEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final String tierId;
	private final String titleId;
	private final String nextTitleId;

	public PlayerTitleLeaveEvent(Player player, String tierId, String titleId, String nextTitleId) {
		this.player = player;
		this.tierId = tierId;
		this.titleId = titleId;
		this.nextTitleId = nextTitleId;
	}

	public Player getPlayer() {
		return player;
	}

	public String getTierId() {
		return tierId;
	}

	public String getTitleId() {
		return titleId;
	}

	public String getNextTitleId() {
		return nextTitleId;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
