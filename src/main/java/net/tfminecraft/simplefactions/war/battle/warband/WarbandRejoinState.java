package net.tfminecraft.simplefactions.war.battle.warband;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.tlibs.character.CharacterRef;

public final class WarbandRejoinState {
	private final String warbandId;
	private final String factionId;
	private final Warband originalWarband;
	private final CharacterRef character;

	public WarbandRejoinState(String warbandId, Faction faction, CharacterRef character) {
		this.warbandId = warbandId;
		this.factionId = faction != null ? faction.getId() : null;
		this.originalWarband = WarbandManager.getByString(warbandId);
		this.character = character;
	}

	/** Whether the character that quit is the one now playing. */
	public boolean isFor(CharacterRef ref) {
		return character != null && character.equals(ref);
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
