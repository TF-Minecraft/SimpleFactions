package net.tfminecraft.simplefactions.war.wounds;

import java.util.List;

/** Ready-made battle forces for tests that only need a side to bring some slots. */
public final class TestForces {
	public static final int LIVES_PER_SLOT = 5;

	private TestForces() {
	}

	/** One faction's paid professional slots, or no force at all for 0. */
	public static List<BattleForce> regiments(int slots) {
		return regiments("test", slots);
	}

	public static List<BattleForce> regiments(String factionId, int slots) {
		if (slots <= 0) {
			return List.of();
		}
		return List.of(faction(factionId, paid("professional", slots)));
	}

	public static BattleForce faction(String factionId, ForcePart... parts) {
		return new BattleForce(ForceKind.REGIMENTS, factionId, LIVES_PER_SLOT, List.of(parts));
	}

	public static BattleForce levy(String factionId, ForcePart... parts) {
		return new BattleForce(ForceKind.LEVY, factionId, LIVES_PER_SLOT, List.of(parts));
	}

	public static BattleForce mercenary(String guildId, int slots) {
		return new BattleForce(ForceKind.MERCENARY, guildId, LIVES_PER_SLOT,
				List.of(new ForcePart("mercenary", false, 0, slots)));
	}

	public static ForcePart paid(String regimentId, int slots) {
		return new ForcePart(regimentId, false, 0, slots);
	}

	public static ForcePart law(String regimentId, int slots) {
		return new ForcePart(regimentId, false, slots, 0);
	}

	public static ForcePart militia(int lawSlots, int paidSlots) {
		return new ForcePart("militia", true, lawSlots, paidSlots);
	}
}
