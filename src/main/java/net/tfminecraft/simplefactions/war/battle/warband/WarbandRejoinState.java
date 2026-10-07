package net.tfminecraft.simplefactions.war.battle.warband;

import java.util.UUID;

import net.tfminecraft.simplefactions.objects.Faction;

public final class WarbandRejoinState {
	private final String warbandId;
	private final String factionId;
	private final Warband originalWarband;

	public WarbandRejoinState(String warbandId, Faction faction) {
		this.warbandId = warbandId;
		this.factionId = faction != null ? faction.getId() : null;
		this.originalWarband = WarbandManager.getByString(warbandId);
	}

	boolean belongsTo(Warband warband) {
		return originalWarband == warband;
	}

	public String getWarbandId() {
		return warbandId;
	}

	public String getFactionId() {
		return factionId;
	}

	public boolean hasFaction() {
		return factionId != null;
	}
}
