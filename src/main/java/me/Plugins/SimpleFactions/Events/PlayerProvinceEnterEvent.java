package me.Plugins.SimpleFactions.Events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerProvinceEnterEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final int provinceId;
	private final Integer previousProvinceId;

	public PlayerProvinceEnterEvent(Player player, int provinceId, Integer previousProvinceId) {
		this.player = player;
		this.provinceId = provinceId;
		this.previousProvinceId = previousProvinceId;
	}

	public Player getPlayer() {
		return player;
	}

	public int getProvinceId() {
		return provinceId;
	}

	public Integer getPreviousProvinceId() {
		return previousProvinceId;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
