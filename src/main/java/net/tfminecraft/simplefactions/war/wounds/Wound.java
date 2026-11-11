package net.tfminecraft.simplefactions.war.wounds;

/**
 * Lives one regiment of one force lost in a battle. The law share is gone once the battle ends;
 * the paid share stays as wounds.
 */
public record Wound(ForceKind kind, String ownerId, String regimentId, int lawLives, int paidLives) {
	public int lives() {
		return lawLives + paidLives;
	}
}
