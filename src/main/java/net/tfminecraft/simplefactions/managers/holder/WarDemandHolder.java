package net.tfminecraft.simplefactions.managers.holder;

import net.tfminecraft.simplefactions.enums.SFGUI;

/** The war demand screens: which side's second demand, and who it is for once chosen. */
public class WarDemandHolder extends WarInventoryHolder {
	private final boolean attackerSide;
	private final String beneficiaryId;

	public WarDemandHolder(int warId, SFGUI type, boolean attackerSide, String beneficiaryId) {
		super(warId, type);
		this.attackerSide = attackerSide;
		this.beneficiaryId = beneficiaryId;
	}

	public boolean isAttackerSide() {
		return attackerSide;
	}

	public String getBeneficiaryId() {
		return beneficiaryId;
	}
}
