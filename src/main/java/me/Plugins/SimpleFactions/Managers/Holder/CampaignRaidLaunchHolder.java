package me.Plugins.SimpleFactions.Managers.Holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class CampaignRaidLaunchHolder implements InventoryHolder {
	private final int warId;
	private final String sourceInstallationId;

	public CampaignRaidLaunchHolder(int warId, String sourceInstallationId) {
		this.warId = warId;
		this.sourceInstallationId = sourceInstallationId;
	}

	public int getWarId() {
		return warId;
	}

	public String getSourceInstallationId() {
		return sourceInstallationId;
	}

	public boolean isSourcePage() {
		return sourceInstallationId == null || sourceInstallationId.isBlank();
	}

	@Override
	public Inventory getInventory() {
		return null;
	}
}
