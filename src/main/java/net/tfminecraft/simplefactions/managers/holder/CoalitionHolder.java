package net.tfminecraft.simplefactions.managers.holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** The coalition list, or one coalition's menu. */
public class CoalitionHolder implements InventoryHolder {
	public enum Type {
		LIST,
		MENU
	}

	private final Type type;
	private final String coalitionId;
	private final int page;

	public CoalitionHolder(Type type, String coalitionId, int page) {
		this.type = type;
		this.coalitionId = coalitionId;
		this.page = page;
	}

	public Type getType() {
		return type;
	}

	public String getCoalitionId() {
		return coalitionId;
	}

	public int getPage() {
		return page;
	}

	@Override
	public Inventory getInventory() {
		return null;
	}
}
