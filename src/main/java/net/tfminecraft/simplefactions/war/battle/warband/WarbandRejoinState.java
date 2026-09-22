package net.tfminecraft.simplefactions.war.battle.warband;

import java.util.UUID;

import net.tfminecraft.simplefactions.objects.Faction;

public final class WarbandRejoinState {
	private final String warbandId;
	private final String factionId;

	public WarbandRejoinState(String warbandId, Faction faction) {
		this.warbandId = warbandId;
		this.factionId = faction != null ? faction.getId() : null;
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
