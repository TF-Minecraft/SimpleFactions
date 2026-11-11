package net.tfminecraft.simplefactions.war.wounds;

import java.util.List;

/**
 * What one owner brought to one side of a battle. {@code ownerId} is a faction id, or the
 * guild id of a mercenary company. Each slot is worth {@code livesPerSlot} lives in the
 * side's shared pool.
 */
public record BattleForce(ForceKind kind, String ownerId, int livesPerSlot, List<ForcePart> parts) {
	public BattleForce {
		livesPerSlot = Math.max(0, livesPerSlot);
		parts = parts == null ? List.of() : List.copyOf(parts);
	}

	public int slots() {
		long slots = 0;
		for (ForcePart part : parts) {
			slots += part.slots();
		}
		return (int) Math.min(Integer.MAX_VALUE, slots);
	}

	public int lives() {
		return (int) Math.min(Integer.MAX_VALUE, (long) slots() * livesPerSlot);
	}

	/** Lives over every force, kept within an int. */
	public static int lives(List<BattleForce> forces) {
		long lives = 0;
		for (BattleForce force : forces) {
			lives += force.lives();
		}
		return (int) Math.min(Integer.MAX_VALUE, lives);
	}

	/** Slots over every force except mercenaries, the side's own regiments for capability checks. */
	public static int regimentSlots(List<BattleForce> forces) {
		long slots = 0;
		for (BattleForce force : forces) {
			if (force.kind() != ForceKind.MERCENARY) {
				slots += force.slots();
			}
		}
		return (int) Math.min(Integer.MAX_VALUE, slots);
	}
}
