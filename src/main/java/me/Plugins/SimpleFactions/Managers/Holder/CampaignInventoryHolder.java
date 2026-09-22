package me.Plugins.SimpleFactions.Managers.Holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import me.Plugins.SimpleFactions.enums.SFGUI;

public class CampaignInventoryHolder implements InventoryHolder {
	private final int warId;
	private final SFGUI type;

	public CampaignInventoryHolder(int warId, SFGUI type) {
		this.warId = warId;
		this.type = type;
	}

	public int getWarId() {
		return warId;
	}

	public SFGUI getType() {
		return type;
	}

	@Override
	public Inventory getInventory() {
		return null;
	}
}
