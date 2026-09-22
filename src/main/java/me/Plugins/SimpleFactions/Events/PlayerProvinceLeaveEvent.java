package me.Plugins.SimpleFactions.Events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerProvinceLeaveEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final int provinceId;
	private final Integer nextProvinceId;

	public PlayerProvinceLeaveEvent(Player player, int provinceId, Integer nextProvinceId) {
		this.player = player;
		this.provinceId = provinceId;
		this.nextProvinceId = nextProvinceId;
	}

	public Player getPlayer() {
		return player;
	}

	public int getProvinceId() {
		return provinceId;
	}

	public Integer getNextProvinceId() {
		return nextProvinceId;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
