package net.tfminecraft.simplefactions.managers.holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.tfminecraft.simplefactions.enums.SFGUI;

public class CampaignInventoryHolder implements InventoryHolder {
	private final int warId;
	private final SFGUI type;
	private int page;

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

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = Math.max(0, page);
	}

	@Override
	public Inventory getInventory() {
		return null;
	}
}
