package me.Plugins.SimpleFactions.Managers.Holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import me.Plugins.SimpleFactions.enums.SFGUI;

public class DeclareWarHolder implements InventoryHolder {
	private final String attackerId;
	private final String defenderId;
	private final SFGUI step;
	private final String governmentLawId;
	private final String leadershipLawId;
	private final String pickingGroupId;

	public DeclareWarHolder(String attackerId, String defenderId, SFGUI step) {
		this(attackerId, defenderId, step, null, null, null);
	}

	public DeclareWarHolder(
			String attackerId,
			String defenderId,
			SFGUI step,
			String governmentLawId,
			String leadershipLawId) {
		this(attackerId, defenderId, step, governmentLawId, leadershipLawId, null);
	}

	public DeclareWarHolder(
			String attackerId,
			String defenderId,
			SFGUI step,
			String governmentLawId,
			String leadershipLawId,
			String pickingGroupId) {
		this.attackerId = attackerId;
		this.defenderId = defenderId;
		this.step = step;
		this.governmentLawId = governmentLawId;
		this.leadershipLawId = leadershipLawId;
		this.pickingGroupId = pickingGroupId;
	}

	public String getAttackerId() {
		return attackerId;
	}

	public String getDefenderId() {
		return defenderId;
	}

	public SFGUI getStep() {
		return step;
	}

	public String getGovernmentLawId() {
		return governmentLawId;
	}

	public String getLeadershipLawId() {
		return leadershipLawId;
	}

	public String getPickingGroupId() {
		return pickingGroupId;
	}

	@Override
	public Inventory getInventory() {
		return null;
	}
}
