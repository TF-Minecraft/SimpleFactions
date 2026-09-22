package me.Plugins.SimpleFactions.Events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerTitleEnterEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final String tierId;
	private final String titleId;
	private final String titleName;
	private final String previousTitleId;

	public PlayerTitleEnterEvent(
			Player player,
			String tierId,
			String titleId,
			String titleName,
			String previousTitleId) {
		this.player = player;
		this.tierId = tierId;
		this.titleId = titleId;
		this.titleName = titleName;
		this.previousTitleId = previousTitleId;
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

	public String getTitleName() {
		return titleName;
	}

	public String getPreviousTitleId() {
		return previousTitleId;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
